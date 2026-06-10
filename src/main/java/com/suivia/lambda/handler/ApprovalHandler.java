package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Audit;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Erp;
import com.suivia.lambda.shared.Json;
import com.suivia.lambda.shared.Val;
import com.suivia.lambda.shared.Ws;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** POST /invoices/{id}/approve | POST /invoices/{id}/reject (RN06) */
public class ApprovalHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String MATCH = System.getenv("MATCH_TABLE");
    private static final String AUDIT = System.getenv("AUDIT_TABLE");
    private static final String STAGING = System.getenv("STAGING_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String path = event.getPath() == null ? "" : event.getPath();
        Map<String, String> params = event.getPathParameters();
        String invId = params == null ? "" : Val.str(params.get("id"));
        Map<String, Object> body = Json.readMap(event.getBody());
        String user = Api.user(event);

        Map<String, Object> item = Dynamo.getItem(MATCH, "id", invId);
        if (item == null) {
            return Api.err("Invoice not found", 404);
        }

        String newStatus;
        if (path.contains("reject")) {
            String reason = Val.str(body.get("reason"));
            if (reason.length() < 5) {
                return Api.err("Rejection reason required (min 5 chars)");
            }
            newStatus = "REJEITADA";
        } else {
            String note = Val.str(body.get("note"));
            String current = Val.str(item.get("status"));
            if (note.length() < 30 && !List.of("APROVADA", "DIVERGENTE_BAIXA").contains(current)) {
                return Api.err("Approval note required (min 30 chars) for divergent invoices — RN06");
            }
            newStatus = "approved";
        }

        Map<String, Object> before = new HashMap<>(item);
        String noteOrReason = body.containsKey("note")
                ? Val.str(body.get("note"))
                : Val.str(body.get("reason"));

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);
        updates.put("approved_by", user);
        updates.put("approval_note", noteOrReason);
        updates.put("approved_at", Instant.now().toString());
        Dynamo.update(MATCH, "id", invId, updates);

        Map<String, Object> after = new HashMap<>(before);
        after.put("status", newStatus);
        Map<String, Object> meta = new HashMap<>();
        meta.put("ip", Api.sourceIp(event));
        Audit.write(AUDIT, invId, "MANUAL_" + newStatus.toUpperCase(), user, before, after, meta);

        Ws.broadcast("INVOICE_UPDATED", Map.of("match_id", invId, "status", newStatus));

        // RF11 — manual decisions are reflected in the ERP (accounts payable posting / payment block)
        if (newStatus.equals("approved")) {
            Map<String, Object> staging = Dynamo.getItem(STAGING, "id", Val.str(item.get("staging_id")));
            String poId = Val.str(item.get("purchase_order_id"));
            Map<String, Object> po = poId.isEmpty() ? null : Map.of("id", poId);
            Erp.postAccountsPayable(staging, po, invId);
        } else if (newStatus.equals("REJEITADA")) {
            Erp.blockPayment(invId, noteOrReason);
        }

        return Api.ok(Map.of("id", invId, "status", newStatus, "approved_by", user));
    }
}
