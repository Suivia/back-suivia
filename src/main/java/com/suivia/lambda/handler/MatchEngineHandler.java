package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Erp;
import com.suivia.lambda.shared.Json;
import com.suivia.lambda.shared.Similarity;
import com.suivia.lambda.shared.Val;
import com.suivia.lambda.shared.Ws;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF08 — 3-way match engine. Weights: CNPJ 25% (blocking, RN03), value 25%,
 * items 25%, taxes 15%, issue-date 10%. RF07 PO lookup, RF09 tolerances.
 * Triggered by SQS (match queue).
 */
public class MatchEngineHandler implements RequestHandler<SQSEvent, Void> {

    private static final String STAGING = System.getenv("STAGING_TABLE");
    private static final String MATCH = System.getenv("MATCH_TABLE");
    private static final String TOLERANCE = System.getenv("TOLERANCE_TABLE");
    private static final String AUDIT = System.getenv("AUDIT_TABLE");

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            Map<String, Object> body = Json.readMap(record.getBody());
            Object sefaz = body.get("sefaz_status");
            processMatch(Val.str(body.get("staging_id")), sefaz == null ? null : Val.str(sefaz));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void processMatch(String stagingId, String sefazOverride) {
        Map<String, Object> staging = Dynamo.getItem(STAGING, "id", stagingId);
        if (staging == null) {
            System.out.println("Staging " + stagingId + " not found");
            return;
        }

        String sefazStatus = sefazOverride != null ? sefazOverride : Val.str(staging.get("sefaz_status"), "pending");
        String tenant = Val.str(staging.get("tenant_id"), "default");

        // RN03 — SEFAZ cancelled = immediate critical rejection
        if (sefazStatus.equals("cancelled")) {
            saveMatch(stagingId, 0, "REJEITADA_CRITICA", Map.of("reason", "SEFAZ_CANCELLED"),
                    new ArrayList<>(), Map.of(), "", null);
            return;
        }

        double nfTotal = Val.dbl(staging.get("total_amount"));
        Map<String, Object> po = findPurchaseOrder(staging, nfTotal);

        long score = 0;
        List<Map<String, Object>> divergences = new ArrayList<>();
        Map<String, Object> headerMatch = new HashMap<>();

        // Criterion 1: supplier CNPJ (25%) — BLOCKING
        String nfCnpj = Val.str(staging.get("supplier_cnpj"));
        String poCnpj = po != null ? Val.str(po.get("supplier_cnpj")) : null;
        if (po != null && nfCnpj.equals(poCnpj)) {
            score += 25;
            headerMatch.put("cnpj", true);
        } else {
            headerMatch.put("cnpj", false);
            divergences.add(makeDiverg("cnpj_mismatch", "critical", "supplier_cnpj",
                    po != null ? Val.str(po.get("supplier_cnpj"), "?") : "no_po",
                    Val.str(staging.get("supplier_cnpj"), "?"), null, false, ""));
            saveMatch(stagingId, 0, "REJEITADA_CRITICA", headerMatch, divergences, Map.of(), "", null);
            return;
        }

        // Criterion 2: total value (25%) — RF09/RN05 tolerance may be supplier-specific
        double poTotal = po != null ? Val.dbl(po.get("total_amount")) : 0;
        Map<String, Object> valueTolRule = getTolerance(tenant, "value", nfCnpj);
        double tolValue = Val.dbl(valueTolRule.get("threshold"));
        String valueAdjAccount = Val.str(valueTolRule.get("adjustment_account"));
        double valDiff = Math.abs(nfTotal - poTotal);
        if (valDiff == 0) {
            score += 25;
            headerMatch.put("value", true);
        } else if (poTotal > 0 && (valDiff / poTotal) <= tolValue) {
            score += 25;
            headerMatch.put("value", "tolerance");
            divergences.add(makeDiverg("price", "low", "total_amount", poTotal, nfTotal, valDiff, true, valueAdjAccount));
        } else {
            score += 10;
            headerMatch.put("value", false);
            divergences.add(makeDiverg("price", "high", "total_amount", poTotal, nfTotal, valDiff, false, ""));
        }

        // Criterion 3: items / quantity (25%)
        List<Map<String, Object>> nfItems = toMapList(Json.readList(Val.str(staging.get("extracted_items"), "[]")));
        List<Map<String, Object>> poItems = po != null ? toMapList(po.get("items")) : new ArrayList<>();
        Map<String, Object> qtyTolRule = getTolerance(tenant, "quantity", nfCnpj);
        Object[] itemResult = matchItems(nfItems, poItems, Val.dbl(qtyTolRule.get("threshold")),
                Val.str(qtyTolRule.get("adjustment_account")));
        score += (long) itemResult[0];
        divergences.addAll((List<Map<String, Object>>) itemResult[1]);
        List<Map<String, Object>> itemsMatch = (List<Map<String, Object>>) itemResult[2];

        // Criterion 4: taxes (15%)
        Map<String, Object> taxInfo = Json.readMap(Val.str(staging.get("tax_info"), "{}"));
        Map<String, Object> poTax = po != null ? toMap(po.get("tax")) : new HashMap<>();
        Object[] taxResult = matchTaxes(taxInfo, poTax);
        score += (long) taxResult[0];
        divergences.addAll((List<Map<String, Object>>) taxResult[1]);
        Map<String, Object> taxMatch = (Map<String, Object>) taxResult[2];

        // Criterion 5: issue date (10%)
        Object[] dateResult = matchDate(Val.str(staging.get("issue_date")));
        score += (long) dateResult[0];
        if (dateResult[1] != null) {
            divergences.add((Map<String, Object>) dateResult[1]);
        }

        // RN08 — NFSe/manual notes without an XML key require mandatory manual review,
        // so they can never be classified as touchless-approved (score capped at 99).
        if (score == 100 && Boolean.TRUE.equals(staging.get("requires_manual_validation"))) {
            score = 99;
        }

        String status = classify(score);
        String matchId = saveMatch(stagingId, score, status, headerMatch, divergences, taxMatch,
                po != null ? Val.str(po.get("id")) : "", itemsMatch);

        if (status.equals("APROVADA")) {
            Erp.postAccountsPayable(staging, po, matchId);
        } else if (status.equals("REJEITADA") || status.equals("REJEITADA_CRITICA")) {
            Erp.blockPayment(matchId, status);
        }

        postToleranceAdjustments(matchId, divergences);
        checkToleranceLearning(tenant, staging, divergences);
    }

    /** RN05 — every applied tolerance generates a posting to the configured adjustment account. */
    private void postToleranceAdjustments(String matchId, List<Map<String, Object>> divergences) {
        for (Map<String, Object> d : divergences) {
            if (!Boolean.TRUE.equals(d.get("tolerance_applied"))) {
                continue;
            }
            String account = Val.str(d.get("adjustment_account"));
            double amount = Val.dbl(d.get("difference"));
            Erp.postAdjustment(matchId, account, amount, Val.str(d.get("divergence_type")));
        }
    }

    /**
     * RN07 — if the same supplier triggers the same tolerance-applied divergence 5x,
     * create a suggested tolerance rule. Counters live in the tolerance table WITHOUT a
     * tenant_id, so they stay out of the tenant-index (and out of the settings GET).
     */
    private void checkToleranceLearning(String tenant, Map<String, Object> staging,
                                        List<Map<String, Object>> divergences) {
        String cnpj = Val.str(staging.get("supplier_cnpj"));
        if (cnpj.isEmpty()) {
            return;
        }
        for (Map<String, Object> d : divergences) {
            if (!Boolean.TRUE.equals(d.get("tolerance_applied"))) {
                continue;
            }
            String dtype = Val.str(d.get("divergence_type"));
            String tolType = dtype.equals("price") ? "value" : (dtype.equals("quantity") ? "quantity" : null);
            if (tolType == null) {
                continue;
            }
            String counterId = "learn#" + tenant + "#" + cnpj + "#" + tolType;
            long count = Dynamo.increment(TOLERANCE, "id", counterId, "count", 1);
            if (count == 5) {
                Map<String, Object> rule = new HashMap<>();
                rule.put("id", UUID.randomUUID().toString());
                rule.put("tenant_id", tenant);
                rule.put("type", tolType);
                rule.put("threshold", tolType.equals("quantity") ? 0.05 : 0.02);
                rule.put("supplier_cnpj", cnpj);
                rule.put("suggested", true);
                rule.put("source", "auto-learned");
                rule.put("created_at", Instant.now().toString());
                Dynamo.putItem(TOLERANCE, rule);
            }
        }
    }

    // ───────────────────────── PO lookup ─────────────────────────

    /** RF07 — picks the open PO for this CNPJ whose total is closest to the invoice total. */
    private Map<String, Object> findPurchaseOrder(Map<String, Object> staging, double nfTotal) {
        String cnpj = Val.str(staging.get("supplier_cnpj"));
        String hint = Val.str(staging.get("po_hint"));
        List<Map<String, Object>> candidates = Erp.listPurchaseOrders(cnpj, hint);
        if (candidates.isEmpty()) {
            return null;
        }
        if (!hint.isEmpty()) {
            for (Map<String, Object> c : candidates) {
                if (hint.equals(Val.str(c.get("id"))) || hint.equals(Val.str(c.get("number")))) {
                    return c;
                }
            }
        }
        return candidates.stream()
                .min(Comparator.comparingDouble(c -> Math.abs(Val.dbl(c.get("total_amount")) - nfTotal)))
                .orElse(candidates.get(0));
    }

    // ───────────────────────── scoring ─────────────────────────

    private Object[] matchItems(List<Map<String, Object>> nfItems, List<Map<String, Object>> poItems,
                                 double qtyTol, String adjAccount) {
        double score = 0;
        List<Map<String, Object>> divs = new ArrayList<>();
        List<Map<String, Object>> matches = new ArrayList<>();
        double maxPerItem = !poItems.isEmpty() ? 25.0 / Math.max(poItems.size(), 1) : 25.0;

        for (Map<String, Object> poItem : poItems) {
            String desc = Val.str(poItem.get("description"));
            Map<String, Object> best = findBestItemMatch(poItem, nfItems);
            if (best == null) {
                divs.add(makeDiverg("quantity", "high", desc, Val.dbl(poItem.get("quantity")), 0, null, false, ""));
                Map<String, Object> m = new HashMap<>();
                m.put("po_item", desc);
                m.put("nf_item", null);
                m.put("similarity", 0);
                m.put("qty_ok", false);
                matches.add(m);
                continue;
            }

            double similarity = (double) best.get("similarity");
            Map<String, Object> nfItem = (Map<String, Object>) best.get("item");
            double qtyPo = Val.dbl(poItem.get("quantity"));
            double qtyNf = Val.dbl(nfItem.get("quantity"));
            double qtyDiff = Math.abs(qtyPo - qtyNf);
            boolean qtyOk = (qtyDiff == 0) || (qtyPo > 0 && qtyDiff / qtyPo <= qtyTol);

            if (similarity >= 0.85 && qtyOk) {
                score += maxPerItem;
            } else if (similarity >= 0.6) {
                score += maxPerItem * 0.5;
                divs.add(makeDiverg("quantity", "medium", desc, qtyPo, qtyNf, qtyDiff, qtyDiff == 0,
                        qtyDiff == 0 ? adjAccount : ""));
            } else {
                divs.add(makeDiverg("quantity", "high", desc, qtyPo, qtyNf, qtyDiff, false, ""));
            }

            Map<String, Object> m = new HashMap<>();
            m.put("po_item", desc);
            m.put("nf_item", Val.str(nfItem.get("description")));
            m.put("similarity", Math.round(similarity * 100.0) / 100.0);
            m.put("qty_po", qtyPo);
            m.put("qty_nf", qtyNf);
            m.put("qty_ok", qtyOk);
            matches.add(m);
        }

        long capped = Math.min(Math.round(score), 25);
        return new Object[]{capped, divs, matches};
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findBestItemMatch(Map<String, Object> poItem, List<Map<String, Object>> nfItems) {
        Map<String, Object> best = null;
        double bestScore = 0;
        String poNcm = Val.str(poItem.get("ncm"));
        String poEan = Val.str(poItem.get("ean"));
        String poDesc = Val.str(poItem.get("description")).toLowerCase();

        for (Map<String, Object> nfItem : nfItems) {
            if (!poNcm.isEmpty() && poNcm.equals(Val.str(nfItem.get("ncm")))) {
                return result(nfItem, 1.0);
            }
            if (!poEan.isEmpty() && poEan.equals(Val.str(nfItem.get("ean")))) {
                return result(nfItem, 1.0);
            }
            double sim = Similarity.ratio(poDesc, Val.str(nfItem.get("description")).toLowerCase());
            if (sim > bestScore) {
                bestScore = sim;
                best = result(nfItem, sim);
            }
        }
        return bestScore >= 0.5 ? best : null;
    }

    private Map<String, Object> result(Map<String, Object> item, double similarity) {
        Map<String, Object> r = new HashMap<>();
        r.put("item", item);
        r.put("similarity", similarity);
        return r;
    }

    private Object[] matchTaxes(Map<String, Object> taxInfo, Map<String, Object> poTax) {
        double score = 0;
        List<Map<String, Object>> divs = new ArrayList<>();
        Map<String, Object> taxM = new HashMap<>();
        for (String type : List.of("icms", "ipi", "pis", "cofins")) {
            double nfVal = Val.dbl(taxInfo.get(type));
            double poVal = Val.dbl(poTax.get(type));
            double diff = Math.abs(nfVal - poVal);
            boolean ok = diff <= 1.0; // RN05 rounding up to R$1 auto-approved
            Map<String, Object> entry = new HashMap<>();
            entry.put("nf", nfVal);
            entry.put("po", poVal);
            entry.put("ok", ok);
            taxM.put(type, entry);
            if (ok) {
                score += 3.75;
            } else {
                divs.add(makeDiverg("tax", "medium", type, poVal, nfVal, diff, false, ""));
            }
        }
        long capped = Math.min(Math.round(score), 15);
        return new Object[]{capped, divs, taxM};
    }

    private Object[] matchDate(String issueDate) {
        if (issueDate == null || issueDate.isEmpty()) {
            return new Object[]{5L, null};
        }
        try {
            OffsetDateTime dt = OffsetDateTime.parse(issueDate.replace("Z", "+00:00"));
            long diffDays = Math.abs(ChronoUnit.DAYS.between(dt.toInstant(), Instant.now()));
            if (diffDays <= 60) {
                return new Object[]{10L, null};
            }
            return new Object[]{5L, makeDiverg("date", "low", "issue_date", "< 60 days", diffDays + " days", null, false, "")};
        } catch (Exception e) {
            return new Object[]{5L, null};
        }
    }

    private String classify(long score) {
        if (score == 100) {
            return "APROVADA";
        }
        if (score >= 90) {
            return "DIVERGENTE_BAIXA";
        }
        if (score >= 70) {
            return "DIVERGENTE_MEDIA";
        }
        return "REJEITADA";
    }

    /**
     * RF09/RN05 — resolves the tolerance rule for {@code type}, preferring a rule scoped to
     * {@code supplierCnpj} over the tenant-wide rule. Returns {@code threshold} (default if no
     * rule matches) and {@code adjustment_account} (empty if not configured).
     */
    private Map<String, Object> getTolerance(String tenant, String type, String supplierCnpj) {
        double def = type.equals("quantity") ? 0.05 : 0.02;
        Map<String, Object> result = new HashMap<>();
        result.put("threshold", def);
        result.put("adjustment_account", "");
        try {
            List<Map<String, Object>> items = Dynamo.query(TOLERANCE, "tenant-index", "tenant_id", tenant);
            Map<String, Object> tenantWide = null;
            for (Map<String, Object> item : items) {
                if (!type.equals(Val.str(item.get("type")))) {
                    continue;
                }
                String ruleCnpj = Val.str(item.get("supplier_cnpj"));
                if (!ruleCnpj.isEmpty() && ruleCnpj.equals(supplierCnpj)) {
                    return ruleResult(item, def);
                }
                if (ruleCnpj.isEmpty() && tenantWide == null) {
                    tenantWide = item;
                }
            }
            if (tenantWide != null) {
                return ruleResult(tenantWide, def);
            }
        } catch (Exception e) {
            // fall through to default
        }
        return result;
    }

    private Map<String, Object> ruleResult(Map<String, Object> rule, double def) {
        Map<String, Object> result = new HashMap<>();
        result.put("threshold", rule.get("threshold") != null ? Val.dbl(rule.get("threshold")) : def);
        result.put("adjustment_account", Val.str(rule.get("adjustment_account")));
        return result;
    }

    // ───────────────────────── persistence ─────────────────────────

    private Map<String, Object> makeDiverg(String dtype, String severity, String field,
                                           Object expected, Object actual, Double diff, boolean tol,
                                           String adjustmentAccount) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", UUID.randomUUID().toString());
        d.put("divergence_type", dtype);
        d.put("severity", severity);
        d.put("field_name", field);
        d.put("expected_value", Val.str(expected));
        d.put("actual_value", Val.str(actual));
        d.put("difference", diff != null ? String.valueOf(diff) : "");
        d.put("tolerance_applied", tol);
        d.put("adjustment_account", adjustmentAccount == null ? "" : adjustmentAccount);
        d.put("resolution", tol ? "auto_approved" : "pending");
        return d;
    }

    private String saveMatch(String stagingId, long score, String status, Map<String, Object> header,
                             List<Map<String, Object>> divergences, Map<String, Object> tax,
                             String poId, List<Map<String, Object>> itemsMatch) {
        String mid = UUID.randomUUID().toString();
        Map<String, Object> match = new HashMap<>();
        match.put("id", mid);
        match.put("staging_id", stagingId);
        match.put("purchase_order_id", poId);
        match.put("match_score", score);
        match.put("status", status);
        match.put("header_match", Json.write(header));
        match.put("items_match", Json.write(itemsMatch != null ? itemsMatch : new ArrayList<>()));
        match.put("tax_match", Json.write(tax));
        match.put("divergences", Json.write(divergences));
        match.put("created_at", Instant.now().toString());
        Dynamo.putItem(MATCH, match);

        Map<String, Object> audit = new HashMap<>();
        audit.put("id", UUID.randomUUID().toString());
        audit.put("entity_id", stagingId);
        audit.put("action", "MATCH_ENGINE_" + status);
        audit.put("user", "system");
        audit.put("after", Json.write(Map.of("score", score, "status", status)));
        audit.put("before", "{}");
        audit.put("meta", "{}");
        audit.put("timestamp", Instant.now().toString());
        Dynamo.putItem(AUDIT, audit);

        Ws.broadcast("INVOICE_UPDATED", Map.of(
                "staging_id", stagingId, "match_id", mid, "status", status, "score", score));
        return mid;
    }

    // ───────────────────────── coercion helpers ─────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toMapList(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object x : list) {
                if (x instanceof Map) {
                    out.add((Map<String, Object>) x);
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new HashMap<>();
    }
}
