package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.suivia.lambda.shared.Aws;
import com.suivia.lambda.shared.Dynamo;
import com.suivia.lambda.shared.Json;
import com.suivia.lambda.shared.Val;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * RF05 — SEFAZ validation. Queries the SEFAZ webservice to confirm the access-key
 * (44 digits) status, with a 24h cache in DynamoDB. RN03 critical-rejection feeds
 * downstream via the match queue.
 * Direct invocation payload: {staging_id, invoice_key}.
 */
public class SefazValidatorHandler implements RequestHandler<Map<String, Object>, Void> {

    private static final String STAGING = System.getenv("STAGING_TABLE");
    private static final String MATCH_QUEUE_URL = System.getenv("MATCH_QUEUE_URL");
    private static final String SEFAZ_WS =
            "https://nfe.fazenda.gov.br/NFeConsultaProtocolo4/NFeConsultaProtocolo4.asmx";

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        String stagingId = Val.str(event.get("staging_id"));
        String invoiceKey = Val.str(event.get("invoice_key"));

        String sefazStatus = "pending";
        if (invoiceKey.length() == 44) {
            Map<String, Object> item = Dynamo.getItem(STAGING, "id", stagingId);
            if (item == null) {
                item = new HashMap<>();
            }
            String cacheExp = Val.str(item.get("sefaz_cache_expires"));
            if (!cacheExp.isEmpty() && isFuture(cacheExp)) {
                sefazStatus = Val.str(item.get("sefaz_status"), "pending");
            } else {
                sefazStatus = querySefaz(invoiceKey);
                String expires = Instant.now().plus(24, ChronoUnit.HOURS).toString();
                Map<String, Object> u = new HashMap<>();
                u.put("sefaz_status", sefazStatus);
                u.put("sefaz_cache_expires", expires);
                Dynamo.update(STAGING, "id", stagingId, u);
            }
        }

        String body = Json.write(Map.of("staging_id", stagingId, "sefaz_status", sefazStatus));
        Aws.SQS.sendMessage(b -> b.queueUrl(MATCH_QUEUE_URL).messageBody(body));
        return null;
    }

    private static boolean isFuture(String iso) {
        try {
            return OffsetDateTime.parse(iso).toInstant().isAfter(Instant.now());
        } catch (Exception e) {
            try {
                return Instant.parse(iso).isAfter(Instant.now());
            } catch (Exception ex) {
                return false;
            }
        }
    }

    /**
     * Real SEFAZ query via SOAP over mutual-TLS, signed with the A1 (PKCS12) client
     * certificate stored in Secrets Manager (env SEFAZ_SECRET_ARN, JSON
     * {pfx_base64, password}). Without a cert we cannot complete the handshake, so we
     * return "pending" (safe default) rather than guessing the fiscal status.
     */
    private static String querySefaz(String chave) {
        HttpClient client = sefazClient();
        if (client == null) {
            System.out.println("SEFAZ: no client certificate configured; returning pending");
            return "pending";
        }
        try {
            String envelope = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                    + " xmlns:nfe=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4\">"
                    + "<soapenv:Header/><soapenv:Body>"
                    + "<nfe:nfeDadosMsg>"
                    + "<consChNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                    + "<tpAmb>1</tpAmb><xServ>CONSULTAR</xServ><chNFe>" + chave + "</chNFe>"
                    + "</consChNFe></nfe:nfeDadosMsg></soapenv:Body></soapenv:Envelope>";
            HttpRequest req = HttpRequest.newBuilder(URI.create(SEFAZ_WS))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4/nfeConsultaNF")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope))
                    .build();
            String resp = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            if (resp.contains("cStat>100<")) {
                return "authorized";
            }
            if (resp.contains("cStat>101<")) {
                return "cancelled";
            }
            if (resp.contains("cStat>110<")) {
                return "denied";
            }
            return "pending";
        } catch (Exception e) {
            System.out.println("SEFAZ query error: " + e.getMessage());
            return "pending";
        }
    }

    /** Builds an HTTPS client carrying the A1 client cert from Secrets Manager, or null if unavailable. */
    private static HttpClient sefazClient() {
        String arn = System.getenv("SEFAZ_SECRET_ARN");
        if (arn == null || arn.isEmpty()) {
            return null;
        }
        try {
            String secret = Aws.SECRETS.getSecretValue(b -> b.secretId(arn)).secretString();
            Map<String, Object> j = Json.readMap(secret);
            String pfxB64 = Val.str(j.get("pfx_base64"));
            String password = Val.str(j.get("password"));
            if (pfxB64.isEmpty()) {
                return null;
            }
            byte[] pfx = Base64.getDecoder().decode(pfxB64);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(pfx), password.toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password.toCharArray());
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .sslContext(ctx)
                    .build();
        } catch (Exception e) {
            System.out.println("SEFAZ cert load failed: " + e.getMessage());
            return null;
        }
    }
}
