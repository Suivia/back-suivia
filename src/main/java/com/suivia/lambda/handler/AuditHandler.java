package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Val;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** GET /audit — RF14 */
public class AuditHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String AUDIT = System.getenv("AUDIT_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Map<String, String> qp = event.getQueryStringParameters();
        String entityId = qp == null ? null : qp.get("entityId");

        List<Map<String, Object>> items = (entityId != null && !entityId.isEmpty())
                ? Dynamo.query(AUDIT, "entity-index", "entity_id", entityId)
                : Dynamo.scan(AUDIT, 500);

        items.sort(Comparator.comparing(
                (Map<String, Object> x) -> Val.str(x.get("timestamp"))).reversed());

        String format = qp == null ? null : qp.get("format");
        if ("csv".equalsIgnoreCase(format)) {
            return Api.csv("audit-export.csv", toCsv(items));
        }
        return Api.ok(items);
    }

    /** RF14 — exporta a trilha de auditoria em CSV. */
    private String toCsv(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder("timestamp,action,entity_id,user,meta\n");
        for (Map<String, Object> item : items) {
            sb.append(csvField(item.get("timestamp"))).append(',')
              .append(csvField(item.get("action"))).append(',')
              .append(csvField(item.get("entity_id"))).append(',')
              .append(csvField(item.get("user"))).append(',')
              .append(csvField(item.get("meta")))
              .append('\n');
        }
        return sb.toString();
    }

    private String csvField(Object o) {
        String v = Val.str(o);
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
