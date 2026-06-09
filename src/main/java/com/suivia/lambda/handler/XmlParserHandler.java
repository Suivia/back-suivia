package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.suivia.lambda.shared.Aws;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Json;
import com.suivia.lambda.shared.Val;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RF04 — Native NFe/CTe XML parser (reads fiscal tags, no Textract cost).
 * RN01 priority XML over PDF, RN02 duplicate blocking by invoice_key.
 * Direct invocation payload: {staging_id, bucket, key}.
 */
public class XmlParserHandler implements RequestHandler<Map<String, Object>, Void> {

    private static final String STAGING = System.getenv("STAGING_TABLE");
    private static final String SEFAZ_FN = envOr("SEFAZ_FUNCTION", "suivia-sefaz-validator");

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        String stagingId = Val.str(event.get("staging_id"));
        String bucket = Val.str(event.get("bucket"));
        String key = Val.str(event.get("key"));

        byte[] bytes = Aws.S3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();

        Map<String, Object> data;
        try {
            Document doc = parse(bytes);
            Element root = doc.getDocumentElement();
            data = first(root, "infNFe") != null ? parseNfe(root) : parseCte();
        } catch (Exception e) {
            updateStatus(stagingId, "error", e.getMessage());
            return null;
        }

        String invoiceKey = Val.str(data.get("invoice_key"));

        // RN02 — duplicate by invoice_key
        if (!invoiceKey.isEmpty()) {
            List<Map<String, Object>> dup = Dynamo.query(STAGING, "key-index", "invoice_key", invoiceKey);
            if (!dup.isEmpty() && !Val.str(dup.get(0).get("id")).equals(stagingId)) {
                updateStatus(stagingId, "duplicate", "Key " + invoiceKey + " already exists");
                return null;
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("supplier_cnpj", data.get("supplier_cnpj"));
        updates.put("supplier_name", data.get("supplier_name"));
        updates.put("invoice_number", data.get("invoice_number"));
        updates.put("invoice_key", invoiceKey);
        updates.put("total_amount", data.get("total_amount"));
        updates.put("extracted_items", Json.write(data.get("items")));
        updates.put("tax_info", Json.write(data.get("tax")));
        updates.put("issue_date", data.get("issue_date"));
        updates.put("dest_cnpj", Val.str(data.get("dest_cnpj")));
        updates.put("po_hint", Val.str(data.get("po_hint")));
        updates.put("status", "extracted");
        updates.put("processed_at", Instant.now().toString());
        Dynamo.update(STAGING, "id", stagingId, updates);

        // Trigger SEFAZ validation
        String payload = Json.write(Map.of("staging_id", stagingId, "invoice_key", invoiceKey));
        Aws.LAMBDA.invoke(b -> b.functionName(SEFAZ_FN)
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(payload)));
        return null;
    }

    private Map<String, Object> parseNfe(Element root) {
        Element inf = firstOr(first(root, "infNFe"), root);
        Element emit = first(inf, "emit");
        Element dest = first(inf, "dest");
        Element ide = first(inf, "ide");
        Element total = first(inf, "ICMSTot");

        List<Map<String, Object>> items = new ArrayList<>();
        NodeList dets = inf.getElementsByTagNameNS("*", "det");
        for (int i = 0; i < dets.getLength(); i++) {
            Element det = (Element) dets.item(i);
            Element prod = first(det, "prod");
            Map<String, Object> item = new HashMap<>();
            item.put("description", t(prod, "xProd"));
            item.put("ncm", t(prod, "NCM"));
            item.put("ean", t(prod, "cEAN"));
            item.put("quantity", Val.dbl(t(prod, "qCom")));
            item.put("unit_price", Val.dbl(t(prod, "vUnCom")));
            item.put("total", Val.dbl(t(prod, "vProd")));
            item.put("supplier_code", t(prod, "cProd"));
            items.add(item);
        }

        Map<String, Object> tax = new HashMap<>();
        tax.put("icms", Val.dbl(t(total, "vICMS")));
        tax.put("ipi", Val.dbl(t(total, "vIPI")));
        tax.put("pis", Val.dbl(t(total, "vPIS")));
        tax.put("cofins", Val.dbl(t(total, "vCOFINS")));
        tax.put("total", Val.dbl(t(total, "vNF")));

        // chNFe usually lives under protNFe (outside infNFe) — search from the document root.
        Element docRoot = inf.getOwnerDocument().getDocumentElement();
        String chave = t(docRoot, "chNFe");
        if (chave.isEmpty()) {
            // fallback: infNFe Id="NFe<44 digits>"
            String id = inf.getAttribute("Id");
            if (id != null && id.startsWith("NFe")) {
                chave = id.substring(3);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("supplier_cnpj", t(emit, "CNPJ"));
        data.put("supplier_name", t(emit, "xNome"));
        data.put("dest_cnpj", t(dest, "CNPJ"));
        data.put("invoice_number", t(ide, "nNF"));
        data.put("invoice_key", chave);
        data.put("issue_date", t(ide, "dhEmi"));
        data.put("total_amount", Val.dbl(t(total, "vNF")));
        data.put("items", items);
        data.put("tax", tax);
        data.put("po_hint", t(first(inf, "infAdic"), "xPed"));
        return data;
    }

    private Map<String, Object> parseCte() {
        Map<String, Object> data = new HashMap<>();
        data.put("supplier_cnpj", "");
        data.put("supplier_name", "CTe");
        data.put("invoice_number", "");
        data.put("invoice_key", "");
        data.put("total_amount", 0.0);
        data.put("items", new ArrayList<>());
        data.put("tax", new HashMap<>());
        data.put("issue_date", "");
        return data;
    }

    private void updateStatus(String sid, String status, String reason) {
        Map<String, Object> u = new HashMap<>();
        u.put("status", status);
        u.put("error_reason", reason == null ? "" : reason);
        u.put("processed_at", Instant.now().toString());
        Dynamo.update(STAGING, "id", sid, u);
    }

    // ───────── DOM helpers (namespace-agnostic by local name) ─────────

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static Element first(Element scope, String local) {
        if (scope == null) {
            return null;
        }
        NodeList nl = scope.getElementsByTagNameNS("*", local);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private static Element firstOr(Element e, Element fallback) {
        return e != null ? e : fallback;
    }

    private static String t(Element scope, String local) {
        Element e = first(scope, local);
        if (e == null) {
            return "";
        }
        String txt = e.getTextContent();
        return txt == null ? "" : txt.trim();
    }

    private static String envOr(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
