package com.suivia.lambda.shared;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RF11 — ERP integration: accounts-payable posting (touchless and manual approval),
 * RN05 tolerance adjustments to the configured transitory account, and payment
 * blocks for rejected invoices.
 */
public final class Erp {

    private static final String ERP_API_URL = envOr("ERP_API_URL", "");

    private Erp() {}

    /** Lança ordem de contas a pagar no ERP (touchless ou aprovação manual). */
    public static void postAccountsPayable(Map<String, Object> staging, Map<String, Object> po, String matchId) {
        if (ERP_API_URL.isEmpty() || staging == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("match_id", matchId);
            payload.put("invoice_number", staging.get("invoice_number"));
            payload.put("supplier_cnpj", staging.get("supplier_cnpj"));
            payload.put("total_amount", Val.dbl(staging.get("total_amount")));
            payload.put("po_id", po != null ? Val.str(po.get("id")) : "");
            Http.postJson(ERP_API_URL + "/accounts-payable", Json.write(payload), 8);
        } catch (Exception e) {
            System.out.println("ERP accounts-payable post failed: " + e.getMessage());
        }
    }

    /** RN05 — toda tolerância aplicada gera lançamento na conta contábil de ajuste configurada. */
    public static void postAdjustment(String matchId, String account, double amount, String reason) {
        if (ERP_API_URL.isEmpty() || account == null || account.isEmpty() || amount == 0) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("match_id", matchId);
            payload.put("account", account);
            payload.put("amount", amount);
            payload.put("reason", reason);
            Http.postJson(ERP_API_URL + "/adjustments", Json.write(payload), 8);
        } catch (Exception e) {
            System.out.println("ERP adjustment post failed: " + e.getMessage());
        }
    }

    /** RF11 — bloqueia pagamento de notas rejeitadas (críticas ou não). */
    public static void blockPayment(String matchId, String reason) {
        if (ERP_API_URL.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> payload = Map.of("match_id", matchId, "reason", reason);
            Http.postJson(ERP_API_URL + "/payment-blocks", Json.write(payload), 8);
        } catch (Exception e) {
            System.out.println("ERP payment-block post failed: " + e.getMessage());
        }
    }

    /** RF07 — lista pedidos em aberto do CNPJ no ERP (usado para sugestão e busca exata). */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> listPurchaseOrders(String cnpj, String hint) {
        if (ERP_API_URL.isEmpty()) {
            return List.of();
        }
        try {
            String url = ERP_API_URL + "/purchase-orders?cnpj=" + cnpj
                    + (hint != null && !hint.isEmpty() ? "&hint=" + hint : "");
            String resp = Http.get(url, 5);
            List<Object> data = Json.readList(resp);
            return data.stream()
                    .filter(o -> o instanceof Map)
                    .map(o -> (Map<String, Object>) o)
                    .toList();
        } catch (Exception e) {
            System.out.println("ERP PO list failed: " + e.getMessage());
            return List.of();
        }
    }

    private static String envOr(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
