package com.suivia.lambda.shared;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;

import java.util.List;
import java.util.Map;

/**
 * Real-time push to connected WebSocket clients. Best-effort: stale connections
 * (HTTP 410) are pruned, other failures are logged and swallowed so business
 * flows never break on a broadcast error.
 */
public final class Ws {

    private static final String CONNECTIONS = System.getenv("CONNECTIONS_TABLE");
    private static final String ENDPOINT = System.getenv("WS_API_ENDPOINT");

    private Ws() {}

    public static void broadcast(String type, Object payload) {
        if (CONNECTIONS == null || CONNECTIONS.isEmpty() || ENDPOINT == null || ENDPOINT.isEmpty()) {
            return;
        }
        try {
            String message = Json.write(Map.of("type", type, "payload", payload == null ? Map.of() : payload));
            ApiGatewayManagementApiClient client = Aws.apiGwManagement(ENDPOINT);
            List<Map<String, Object>> conns = Dynamo.scan(CONNECTIONS);
            for (Map<String, Object> c : conns) {
                String id = Val.str(c.get("connection_id"));
                if (id.isEmpty()) {
                    continue;
                }
                try {
                    client.postToConnection(b -> b.connectionId(id).data(SdkBytes.fromUtf8String(message)));
                } catch (GoneException ge) {
                    Dynamo.delete(CONNECTIONS, "connection_id", id);
                } catch (Exception e) {
                    System.out.println("WS post failed for " + id + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("WS broadcast failed: " + e.getMessage());
        }
    }
}
