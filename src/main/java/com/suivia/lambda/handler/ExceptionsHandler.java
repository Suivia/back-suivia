package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Audit;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Json;
import com.suivia.lambda.shared.Val;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** GET /exceptions | POST /exceptions/bulk-approve */
public class ExceptionsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String DIVERG = System.getenv("DIVERG_TABLE");
    private static final String MATCH = System.getenv("MATCH_TABLE");
    private static final String AUDIT = System.getenv("AUDIT_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String path = event.getPath() == null ? "" : event.getPath();
        String method = event.getHttpMethod() == null ? "GET" : event.getHttpMethod();
        if (path.contains("bulk") && method.equals("POST")) {
            return bulkApprove(event);
        }
        return getExceptions(event);
    }

    private APIGatewayProxyResponseEvent getExceptions(APIGatewayProxyRequestEvent event) {
        List<Map<String, Object>> items = Dynamo.scan(DIVERG);
        Map<String, String> qp = event.getQueryStringParameters();
        if (qp != null) {
            String severity = qp.get("severity");
            String type = qp.get("type");
            if (severity != null) {
                items.removeIf(i -> !severity.equals(Val.str(i.get("severity"))));
            }
            if (type != null) {
                items.removeIf(i -> !type.equals(Val.str(i.get("divergence_type"))));
            }
        }
        for (Map<String, Object> item : items) {
            String invId = Val.str(item.get("invoice_id"));
            if (!invId.isEmpty()) {
                Map<String, Object> match = Dynamo.getItem(MATCH, "id", invId);
                item.put("match", match == null ? Map.of() : match);
            }
        }
        return Api.ok(items);
    }

    private APIGatewayProxyResponseEvent bulkApprove(APIGatewayProxyRequestEvent event) {
        Map<String, Object> body = Json.readMap(event.getBody());
        @SuppressWarnings("unchecked")
        List<Object> ids = body.get("ids") instanceof List ? (List<Object>) body.get("ids") : new ArrayList<>();
        double maxValue = body.containsKey("maxValue") ? Val.dbl(body.get("maxValue")) : 999999;
        String user = Api.user(event);
        int count = 0;

        for (Object idObj : ids) {
            String did = Val.str(idObj);
            Map<String, Object> item = Dynamo.getItem(DIVERG, "id", did);
            if (item == null) {
                continue;
            }
            double diff = Val.dbl(item.get("difference"));
            if (diff > maxValue) {
                continue;
            }
            Dynamo.update(DIVERG, "id", did, Map.of("resolution", "bulk_approved"));
            Map<String, Object> after = new HashMap<>(item);
            after.put("resolution", "bulk_approved");
            Audit.write(AUDIT, did, "BULK_APPROVE", user, item, after, Map.of());
            count++;
        }
        return Api.ok(Map.of("approved", count));
    }
}
