package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.suivia.lambda.shared.Aws;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Json;
import com.suivia.lambda.shared.Val;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvocationType;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RF01 — Multichannel capture. Triggered by EventBridge on S3 object creation.
 * Creates the staging record and routes to the correct parser.
 */
public class ClassifierHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String STAGING = System.getenv("STAGING_TABLE");
    private static final String XML_PARSER_FN = envOr("XML_PARSER_FUNCTION", "suivia-xml-parser");
    private static final String TEXTRACT_FN = envOr("TEXTRACT_FUNCTION", "suivia-textract");

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> detail = asMap(event.get("detail"));
        String bucket = Val.str(asMap(detail.get("bucket")).get("name"));
        String key = Val.str(asMap(detail.get("object")).get("key"));

        String fileType = key.contains(".")
                ? key.substring(key.lastIndexOf('.') + 1).toLowerCase()
                : "unknown";

        String source = "s3";
        if (key.contains("/email/")) {
            source = "email";
        } else if (key.contains("/upload/")) {
            source = "upload";
        } else if (key.contains("/camera/")) {
            source = "camera";
        } else if (key.contains("/api/")) {
            source = "api";
        }

        String stagingId = UUID.randomUUID().toString();
        String batchId = key.contains("/")
                ? key.substring(0, key.indexOf('/'))
                : "BATCH-" + stagingId.substring(0, 8);

        Map<String, Object> item = new HashMap<>();
        item.put("id", stagingId);
        item.put("batch_id", batchId);
        item.put("source", source);
        item.put("file_url", "s3://" + bucket + "/" + key);
        item.put("file_type", fileType);
        item.put("status", "processing");
        item.put("created_at", Instant.now().toString());
        item.put("processed_at", null);
        Dynamo.putItem(STAGING, item);

        String target = fileType.equals("xml") ? XML_PARSER_FN : TEXTRACT_FN;
        String payload = Json.write(Map.of("staging_id", stagingId, "bucket", bucket, "key", key));
        Aws.LAMBDA.invoke(b -> b.functionName(target)
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(payload)));

        return Map.of("statusCode", 200, "staging_id", stagingId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private static String envOr(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isEmpty()) ? def : v;
    }
}
