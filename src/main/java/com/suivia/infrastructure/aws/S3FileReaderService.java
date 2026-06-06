package com.suivia.infrastructure.aws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileReaderService {

    private final S3Client s3Client;

    /**
     * Lê um arquivo do S3 a partir de uma URL no formato:
     * s3://nome-do-bucket/caminho/do/arquivo.xml
     *
     * @param s3Url URL no formato s3://bucket/key
     * @return InputStream com o conteúdo do arquivo
     */
    public InputStream readFile(String s3Url) {
        // Extrai bucket e key da URL s3://bucket/key
        String withoutPrefix = s3Url.replace("s3://", "");
        int slashIndex = withoutPrefix.indexOf('/');
        String bucket = withoutPrefix.substring(0, slashIndex);
        String key = withoutPrefix.substring(slashIndex + 1);

        log.info("Lendo arquivo do S3 | bucket={} | key={}", bucket, key);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        log.info("Arquivo lido com sucesso do S3 | key={}", key);

        return response;
    }
}
