package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Json;

import java.util.Map;
import java.util.UUID;

/** GET|POST /settings/tolerances — RF09, RF15 */
public class ToleranceHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TOLERANCE = System.getenv("TOLERANCE_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String method = event.getHttpMethod() == null ? "GET" : event.getHttpMethod();
        String tenant = Api.tenant(event);

        if (method.equals("GET")) {
            return Api.ok(Dynamo.query(TOLERANCE, "tenant-index", "tenant_id", tenant));
        }

        Map<String, Object> body = Json.readMap(event.getBody());
        if (!body.containsKey("id") || body.get("id") == null || body.get("id").toString().isEmpty()) {
            body.put("id", UUID.randomUUID().toString());
        }
        body.put("tenant_id", tenant);
        Dynamo.putItem(TOLERANCE, body);
        return Api.ok(body);
    }
}
