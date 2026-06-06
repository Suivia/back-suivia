package com.suivia.infrastructure.aws;

import com.suivia.infrastructure.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    /**
     * Faz upload do arquivo no S3 e retorna a URL do objeto.
     * Pasta: invoices/{batchId}/{invoiceId}/{nomeOriginal}
     */
    public String uploadInvoice(MultipartFile file, String batchId, String invoiceId) {
        String key = String.format("invoices/%s/%s/%s", batchId, invoiceId, file.getOriginalFilename());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(awsProperties.getS3Bucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            String fileUrl = String.format("s3://%s/%s", awsProperties.getS3Bucket(), key);
            log.info("Arquivo enviado para S3: {}", fileUrl);
            return fileUrl;

        } catch (IOException e) {
            log.error("Erro ao fazer upload do arquivo para S3: {}", e.getMessage());
            throw new RuntimeException("Falha no upload do arquivo para S3", e);
        }
    }
}
