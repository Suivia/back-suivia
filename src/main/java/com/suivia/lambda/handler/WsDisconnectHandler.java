package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Val;

import java.util.Map;

/** WebSocket $disconnect — removes the stored connection id. */
public class WsDisconnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String CONNECTIONS = System.getenv("CONNECTIONS_TABLE");

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> rc = event.get("requestContext") instanceof Map
                ? (Map<String, Object>) event.get("requestContext") : Map.of();
        String connId = Val.str(rc.get("connectionId"));

        if (!connId.isEmpty() && CONNECTIONS != null && !CONNECTIONS.isEmpty()) {
            Dynamo.delete(CONNECTIONS, "connection_id", connId);
        }
        return Map.of("statusCode", 200, "body", "disconnected");
    }
}
