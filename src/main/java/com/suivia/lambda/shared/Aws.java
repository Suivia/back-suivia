package com.suivia.lambda.shared;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.textract.TextractClient;

import java.net.URI;

/**
 * Lazily-instantiated AWS SDK v2 clients, shared across warm Lambda invocations.
 * Uses the URL-connection HTTP client to keep the shaded jar lean (no Netty).
 */
public final class Aws {

    private Aws() {}

    public static final DynamoDbClient DDB = DynamoDbClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    public static final S3Client S3 = S3Client.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    public static final S3Presigner S3_PRESIGNER = S3Presigner.create();

    public static final SqsClient SQS = SqsClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    public static final LambdaClient LAMBDA = LambdaClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    public static final TextractClient TEXTRACT = TextractClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    public static final StsClient STS = StsClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    public static final SecretsManagerClient SECRETS = SecretsManagerClient.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .build();

    /** API Gateway Management client bound to a specific WebSocket stage endpoint (https URL). */
    public static ApiGatewayManagementApiClient apiGwManagement(String endpoint) {
        return ApiGatewayManagementApiClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(URI.create(endpoint))
                .build();
    }
}
