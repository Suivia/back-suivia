package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Val;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GET /batches | GET /batches/{id} */
public class BatchesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String STAGING = System.getenv("STAGING_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Map<String, String> params = event.getPathParameters();
        String bid = params == null ? null : params.get("id");

        if (bid != null && !bid.isEmpty()) {
            List<Map<String, Object>> items = Dynamo.query(STAGING, "batch-index", "batch_id", bid);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("batch_id", bid);
            out.put("items", items);
            out.put("total", items.size());
            return Api.ok(out);
        }

        List<Map<String, Object>> all = Dynamo.scan(STAGING);
        Map<String, Map<String, Object>> batches = new LinkedHashMap<>();
        for (Map<String, Object> item : all) {
            String b = Val.str(item.get("batch_id"), "?");
            Map<String, Object> agg = batches.computeIfAbsent(b, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", k);
                m.put("origin", Val.str(item.get("source"), "?"));
                m.put("total", 0L);
                m.put("processed", 0L);
                m.put("errors", 0L);
                m.put("status", "PROCESSING");
                m.put("started_at", Val.str(item.get("created_at")));
                return m;
            });
            agg.put("total", (Long) agg.get("total") + 1);
            String s = Val.str(item.get("status"));
            if (s.equals("extracted") || s.equals("matched")) {
                agg.put("processed", (Long) agg.get("processed") + 1);
            }
            if (s.equals("error")) {
                agg.put("errors", (Long) agg.get("errors") + 1);
            }
        }
        for (Map<String, Object> b : batches.values()) {
            long total = (Long) b.get("total");
            long processed = (Long) b.get("processed");
            if (total > 0 && processed == total) {
                b.put("status", "CONCLUDED");
            }
        }
        return Api.ok(new ArrayList<>(batches.values()));
    }
}
