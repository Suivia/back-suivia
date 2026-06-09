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
import com.suivia.lambda.shared.Ws;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** POST /invoices/{id}/purchase-order — manual PO association (RF07). */
public class LinkPoHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String MATCH = System.getenv("MATCH_TABLE");
    private static final String AUDIT = System.getenv("AUDIT_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Map<String, String> params = event.getPathParameters();
        String invId = params == null ? "" : Val.str(params.get("id"));
        Map<String, Object> body = Json.readMap(event.getBody());
        String user = Api.user(event);

        String poId = body.containsKey("purchase_order_id")
                ? Val.str(body.get("purchase_order_id"))
                : Val.str(body.get("po_id"));
        if (poId.isEmpty()) {
            return Api.err("purchase_order_id required");
        }

        Map<String, Object> item = Dynamo.getItem(MATCH, "id", invId);
        if (item == null) {
            return Api.err("Invoice not found", 404);
        }

        Map<String, Object> before = new HashMap<>(item);
        Map<String, Object> updates = new HashMap<>();
        updates.put("purchase_order_id", poId);
        updates.put("linked_po", Json.write(body));
        updates.put("po_linked_by", user);
        updates.put("po_linked_at", Instant.now().toString());
        Dynamo.update(MATCH, "id", invId, updates);

        Map<String, Object> after = new HashMap<>(before);
        after.putAll(updates);
        Map<String, Object> meta = new HashMap<>();
        meta.put("ip", Api.sourceIp(event));
        Audit.write(AUDIT, invId, "MANUAL_PO_LINK", user, before, after, meta);

        Ws.broadcast("INVOICE_UPDATED", Map.of("match_id", invId, "purchase_order_id", poId));

        return Api.ok(Map.of("id", invId, "purchase_order_id", poId));
    }
}
