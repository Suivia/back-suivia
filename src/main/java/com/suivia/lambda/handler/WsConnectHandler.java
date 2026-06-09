package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Val;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** WebSocket $connect — persists the connection id for later broadcasts. */
public class WsConnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final String CONNECTIONS = System.getenv("CONNECTIONS_TABLE");

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> rc = event.get("requestContext") instanceof Map
                ? (Map<String, Object>) event.get("requestContext") : Map.of();
        String connId = Val.str(rc.get("connectionId"));

        if (!connId.isEmpty() && CONNECTIONS != null && !CONNECTIONS.isEmpty()) {
            Map<String, Object> item = new HashMap<>();
            item.put("connection_id", connId);
            item.put("connected_at", Instant.now().toString());
            item.put("expires", Instant.now().getEpochSecond() + 7200); // 2h TTL
            Dynamo.putItem(CONNECTIONS, item);
        }
        return Map.of("statusCode", 200, "body", "connected");
    }
}
