package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Json;

import java.util.HashMap;
import java.util.Map;

/** GET|POST /settings/tenant — RF15 (configuração multi-tenant: ERP destino, e-mail, usuários). */
public class TenantConfigHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TENANT = System.getenv("TENANT_TABLE");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String method = event.getHttpMethod() == null ? "GET" : event.getHttpMethod();
        String tenant = Api.tenant(event);

        if (method.equals("GET")) {
            Map<String, Object> item = Dynamo.getItem(TENANT, "tenant_id", tenant);
            return Api.ok(item == null ? Map.of("tenant_id", tenant) : item);
        }

        Map<String, Object> body = Json.readMap(event.getBody());
        Map<String, Object> item = new HashMap<>(body);
        item.put("tenant_id", tenant);
        Dynamo.putItem(TENANT, item);
        return Api.ok(item);
    }
}
