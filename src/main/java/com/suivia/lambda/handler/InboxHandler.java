package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Dynamo;

import java.util.List;
import java.util.Map;

/** GET /invoices/inbox | GET /invoices/{id} */
public class InboxHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String MATCH = System.getenv("MATCH_TABLE");
    private static final String STAGING = System.getenv("STAGING_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String path = event.getPath() == null ? "" : event.getPath();
        Map<String, String> params = event.getPathParameters();
        String id = params == null ? null : params.get("id");
        if (path.contains("{id}") || (id != null && !id.isEmpty())) {
            return getInvoice(id == null ? "" : id);
        }
        return getInbox(event);
    }

    private APIGatewayProxyResponseEvent getInbox(APIGatewayProxyRequestEvent event) {
        Map<String, String> qp = event.getQueryStringParameters();
        String status = qp == null ? null : qp.get("status");
        List<Map<String, Object>> items = (status != null && !status.isEmpty())
                ? Dynamo.query(MATCH, "status-index", "status", status)
                : Dynamo.scan(MATCH, 200);
        for (Map<String, Object> item : items) {
            attachStaging(item);
        }
        return Api.ok(items);
    }

    private APIGatewayProxyResponseEvent getInvoice(String invoiceId) {
        Map<String, Object> item = Dynamo.getItem(MATCH, "id", invoiceId);
        if (item == null) {
            return Api.err("Invoice not found", 404);
        }
        attachStaging(item);
        return Api.ok(item);
    }

    private void attachStaging(Map<String, Object> item) {
        Object sid = item.get("staging_id");
        if (sid != null && !sid.toString().isEmpty()) {
            Map<String, Object> stg = Dynamo.getItem(STAGING, "id", sid.toString());
            item.put("staging", stg == null ? Map.of() : stg);
        }
    }
}
