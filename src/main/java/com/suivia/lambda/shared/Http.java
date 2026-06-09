package com.suivia.lambda.shared;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal HTTP client (JDK built-in) for ERP and SEFAZ calls. */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Http() {}

    public static String get(String url, int timeoutSec) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public static String postJson(String url, String body, int timeoutSec) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    public static String postSoap(String url, String body, String soapAction, int timeoutSec) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", soapAction)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return CLIENT.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }
}
