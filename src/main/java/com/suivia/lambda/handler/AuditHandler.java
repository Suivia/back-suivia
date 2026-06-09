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
        return Api.ok(items);
    }
}
