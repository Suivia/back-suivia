package com.suivia.invoice.service;

import com.suivia.infrastructure.aws.S3StorageService;
import com.suivia.infrastructure.aws.SqsPublisherService;
import com.suivia.invoice.domain.InvoiceStaging;
import com.suivia.invoice.domain.InvoiceStaging.*;
import com.suivia.invoice.dto.InvoiceUploadResponse;
import com.suivia.invoice.repository.InvoiceStagingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceUploadService {

    private final S3StorageService s3StorageService;
    private final SqsPublisherService sqsPublisherService;
    private final InvoiceStagingRepository repository;

    /**
     * Fluxo completo de entrada de uma nota:
     * 1. Detecta o tipo de arquivo (XML, PDF, JPG, PNG)
     * 2. Faz upload no S3
     * 3. Cria registro na tabela de staging com status PROCESSING
     * 4. Publica evento na fila SQS para o Worker processar
     * 5. Retorna resposta imediata para o frontend
     */
    public InvoiceUploadResponse processUpload(MultipartFile file, String batchId) {
        String invoiceId = UUID.randomUUID().toString();
        String resolvedBatchId = (batchId != null && !batchId.isBlank())
                ? batchId
                : UUID.randomUUID().toString();

        log.info("Iniciando upload de NF | invoiceId={} | batchId={} | arquivo={}",
                invoiceId, resolvedBatchId, file.getOriginalFilename());

        // 1. Detectar tipo de arquivo
        FileType fileType = detectFileType(file.getOriginalFilename());

        // 2. Upload no S3
        String fileUrl = s3StorageService.uploadInvoice(file, resolvedBatchId, invoiceId);

        // 3. Criar staging no PostgreSQL
        InvoiceStaging staging = InvoiceStaging.builder()
                .id(invoiceId)
                .batchId(resolvedBatchId)
                .source(InvoiceSource.upload)
                .fileUrl(fileUrl)
                .fileType(fileType)
                .sefazStatus(SefazStatus.pending)
                .status(InvoiceStagingStatus.processing)
                .build();

        repository.save(staging);
        log.info("Staging criado no banco | invoiceId={}", invoiceId);

        // 4. Publicar evento na fila SQS para o Worker
        sqsPublisherService.publishInvoiceEvent(Map.of(
                "invoiceId", invoiceId,
                "batchId", resolvedBatchId,
                "fileUrl", fileUrl,
                "fileType", fileType.name()
        ));

        // 5. Retornar resposta imediata
        return InvoiceUploadResponse.builder()
                .invoiceId(invoiceId)
                .batchId(resolvedBatchId)
                .status("processing")
                .fileUrl(fileUrl)
                .message("Nota fiscal recebida com sucesso. Processamento iniciado.")
                .build();
    }

    private FileType detectFileType(String filename) {
        if (filename == null) return FileType.pdf;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xml")) return FileType.xml;
        if (lower.endsWith(".pdf")) return FileType.pdf;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return FileType.jpg;
        if (lower.endsWith(".png")) return FileType.png;
        return FileType.pdf;
    }
}
