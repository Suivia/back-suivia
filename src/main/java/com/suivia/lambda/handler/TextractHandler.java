package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.suivia.lambda.shared.Aws;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Json;
import com.suivia.lambda.shared.Val;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.textract.model.AnalyzeExpenseResponse;
import software.amazon.awssdk.services.textract.model.BoundingBox;
import software.amazon.awssdk.services.textract.model.ExpenseDocument;
import software.amazon.awssdk.services.textract.model.ExpenseField;
import software.amazon.awssdk.services.textract.model.LineItemFields;
import software.amazon.awssdk.services.textract.model.LineItemGroup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RF03 — AWS Textract AnalyzeExpense for PDF/JPG/PNG, including bounding boxes
 * for frontend highlighting. Direct invocation payload: {staging_id, bucket, key}.
 */
public class TextractHandler implements RequestHandler<Map<String, Object>, Void> {

    private static final String STAGING = System.getenv("STAGING_TABLE");
    private static final String SEFAZ_FN = envOr("SEFAZ_FUNCTION", "suivia-sefaz-validator");
    private static final String MATCH_QUEUE_URL = System.getenv("MATCH_QUEUE_URL");

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        String stagingId = Val.str(event.get("staging_id"));
        String bucket = Val.str(event.get("bucket"));
        String key = Val.str(event.get("key"));

        AnalyzeExpenseResponse resp = Aws.TEXTRACT.analyzeExpense(b -> b.document(
                d -> d.s3Object(s -> s.bucket(bucket).name(key))));

        Map<String, String> summary = new LinkedHashMap<>();
        Map<String, Object> boxes = new LinkedHashMap<>();
        List<Map<String, Object>> lineItems = new ArrayList<>();

        for (ExpenseDocument doc : resp.expenseDocuments()) {
            for (ExpenseField sf : doc.summaryFields()) {
                String ftype = sf.type() != null ? Val.str(sf.type().text()) : "";
                String fval = sf.valueDetection() != null ? Val.str(sf.valueDetection().text()) : "";
                summary.put(ftype, fval);
                if (sf.valueDetection() != null && sf.valueDetection().geometry() != null
                        && sf.valueDetection().geometry().boundingBox() != null) {
                    boxes.put(ftype, bbox(sf.valueDetection().geometry().boundingBox()));
                }
            }
            for (LineItemGroup group : doc.lineItemGroups()) {
                for (LineItemFields li : group.lineItems()) {
                    Map<String, String> fields = new HashMap<>();
                    for (ExpenseField lif : li.lineItemExpenseFields()) {
                        String t = lif.type() != null ? Val.str(lif.type().text()) : "";
                        String v = lif.valueDetection() != null ? Val.str(lif.valueDetection().text()) : "";
                        fields.put(t, v);
                    }
                    Map<String, Object> item = new HashMap<>();
                    item.put("description", fields.getOrDefault("ITEM", fields.getOrDefault("PRODUCT_CODE", "")));
                    item.put("quantity", safeFloat(fields.get("QUANTITY")));
                    item.put("unit_price", safeFloat(fields.get("UNIT_PRICE")));
                    item.put("total", safeFloat(fields.get("PRICE")));
                    lineItems.add(item);
                }
            }
        }

        String supplierCnpj = extractCnpj(orEmpty(summary.get("VENDOR_VAT_NUMBER"), summary.get("VENDOR_NAME")));
        double totalAmount = safeFloat(orEmpty(summary.get("AMOUNT_DUE"), summary.get("TOTAL")));

        Map<String, Object> updates = new HashMap<>();
        updates.put("supplier_cnpj", supplierCnpj);
        updates.put("supplier_name", summary.getOrDefault("VENDOR_NAME", ""));
        updates.put("total_amount", totalAmount);
        updates.put("extracted_items", Json.write(lineItems));
        updates.put("raw_textract_json", Json.write(summary));
        updates.put("bounding_boxes", Json.write(boxes));
        updates.put("status", "extracted");
        updates.put("processed_at", Instant.now().toString());
        Dynamo.update(STAGING, "id", stagingId, updates);

        String invoiceKey = summary.getOrDefault("INVOICE_RECEIPT_ID", "");
        if (invoiceKey != null && invoiceKey.length() == 44) {
            String payload = Json.write(Map.of("staging_id", stagingId, "invoice_key", invoiceKey));
            Aws.LAMBDA.invoke(b -> b.functionName(SEFAZ_FN)
                    .invocationType(InvocationType.EVENT)
                    .payload(SdkBytes.fromUtf8String(payload)));
        } else {
            String body = Json.write(Map.of("staging_id", stagingId));
            Aws.SQS.sendMessage(b -> b.queueUrl(MATCH_QUEUE_URL).messageBody(body));
        }
        return null;
    }

    private static Map<String, Object> bbox(BoundingBox b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Width", b.width());
        m.put("Height", b.height());
        m.put("Left", b.left());
        m.put("Top", b.top());
        return m;
    }

    private static double safeFloat(String v) {
        if (v == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(v.replace("R$", "").replace(",", ".").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String extractCnpj(String text) {
        String digits = (text == null ? "" : text).replaceAll("[^0-9]", "");
        return digits.length() >= 14 ? digits.substring(0, 14) : digits;
    }

    private static String orEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private static String envOr(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
