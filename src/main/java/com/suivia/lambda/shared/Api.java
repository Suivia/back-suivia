package com.suivia.lambda.shared;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

/** REST API Gateway (proxy, v1) request/response helpers — mirrors utils.ok/err/get_tenant/get_user. */
public final class Api {

    private Api() {}

    private static final Map<String, String> HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Headers", "Authorization,Content-Type",
            "Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS"
    );

    public static APIGatewayProxyResponseEvent ok(Object body) {
        return resp(200, body);
    }

    public static APIGatewayProxyResponseEvent ok(Object body, int status) {
        return resp(status, body);
    }

    public static APIGatewayProxyResponseEvent err(String msg) {
        return resp(400, Map.of("error", msg));
    }

    public static APIGatewayProxyResponseEvent err(String msg, int status) {
        return resp(status, Map.of("error", msg));
    }

    public static APIGatewayProxyResponseEvent resp(int status, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(HEADERS)
                .withBody(Json.write(body));
    }

    /** RF14 — CSV export response (e.g. audit trail download). */
    public static APIGatewayProxyResponseEvent csv(String filename, String csvBody) {
        Map<String, String> headers = Map.of(
                "Content-Type", "text/csv",
                "Content-Disposition", "attachment; filename=\"" + filename + "\"",
                "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Headers", "Authorization,Content-Type",
                "Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS"
        );
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(headers)
                .withBody(csvBody);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> claims(APIGatewayProxyRequestEvent e) {
        var rc = e.getRequestContext();
        if (rc == null || rc.getAuthorizer() == null) {
            return Map.of();
        }
        Object c = rc.getAuthorizer().get("claims");
        if (c instanceof Map) {
            return (Map<String, String>) c;
        }
        return Map.of();
    }

    public static String tenant(APIGatewayProxyRequestEvent e) {
        return claims(e).getOrDefault("custom:tenant_id", "default");
    }

    public static String user(APIGatewayProxyRequestEvent e) {
        Map<String, String> c = claims(e);
        return c.getOrDefault("email", c.getOrDefault("sub", "unknown"));
    }

    public static String sourceIp(APIGatewayProxyRequestEvent e) {
        var rc = e.getRequestContext();
        if (rc != null && rc.getIdentity() != null) {
            return rc.getIdentity().getSourceIp();
        }
        return null;
    }
}
