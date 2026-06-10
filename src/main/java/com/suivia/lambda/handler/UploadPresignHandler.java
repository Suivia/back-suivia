package com.suivia.lambda.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.suivia.lambda.shared.Api;
import com.suivia.lambda.shared.Aws;
import com.suivia.lambda.shared.Json;
import com.suivia.lambda.shared.Val;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** POST /invoices/presign — direct-to-S3 upload presigned URL */
public class UploadPresignHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static String bucket;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Map<String, Object> body = Json.readMap(event.getBody());
        String fileName = Val.str(body.get("fileName"), "upload.pdf");
        String source = Val.str(body.get("source"), "upload");
        String tenant = Api.tenant(event);

        String ext = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase()
                : "bin";
        String uid = UUID.randomUUID().toString();
        String s3Key = source + "/" + tenant + "/" + uid + "." + ext;

        PutObjectRequest por = PutObjectRequest.builder()
                .bucket(resolveBucket())
                .key(s3Key)
                .build();
        PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(300))
                .putObjectRequest(por)
                .build();
        String url = Aws.S3_PRESIGNER.presignPutObject(presignReq).url().toString();

        return Api.ok(Map.of("uploadUrl", url, "s3Key", s3Key, "stagingId", uid));
    }

    private static synchronized String resolveBucket() {
        if (bucket == null) {
            String env = System.getenv("RAW_BUCKET");
            bucket = (env != null && !env.isEmpty())
                    ? env
                    : "suivia-raw-" + Aws.STS.getCallerIdentity().account();
        }
        return bucket;
    }
}
