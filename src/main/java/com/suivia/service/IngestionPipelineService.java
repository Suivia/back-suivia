package com.suivia.service;

import com.suivia.domain.entity.InvoiceStaging;
import com.suivia.domain.enums.InvoiceStatus;
import com.suivia.repository.InvoiceStagingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Este serviço simula o EventBridge + StepFunctions da arquitetura.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionPipelineService {

    private final InvoiceStagingRepository stagingRepository;
    private final MatchEngineService matchEngineService;

    @Transactional
    public InvoiceStaging ingestFile(String fileUrl, String source, String fileType) {
        // Step 1: Criação na Staging (Event Driven S3 PUT)
        InvoiceStaging staging = new InvoiceStaging();
        staging.setBatchId("BATCH-" + System.currentTimeMillis());
        staging.setFileUrl(fileUrl);
        staging.setSource(source);
        staging.setFileType(fileType);
        staging.setStatus(InvoiceStatus.PROCESSING);
        staging = stagingRepository.save(staging);

        // Dispara o pipeline assíncrono (Step Functions simulado)
        triggerProcessingPipeline(staging.getId());

        return staging;
    }

    @Async
    @Transactional
    public void triggerProcessingPipeline(UUID stagingId) {
        log.info("Step Functions Start - Lote ID: {}", stagingId);
        try {
            // Simula Extração do Textract/XML Parser e Validação Sefaz
            Thread.sleep(1500); 
            InvoiceStaging staging = stagingRepository.findById(stagingId).orElseThrow();

            // Populando dados simulados da extração
            staging.setSupplierCnpj("72381189000110");
            staging.setInvoiceNumber("000.002.841");
            staging.setInvoiceKey("35230972381189000110550010000028411234567890");
            staging.setTotalAmount(new BigDecimal("25000.00"));
            staging.setSefazStatus("AUTHORIZED");
            staging.setStatus(InvoiceStatus.EXTRACTED);
            stagingRepository.save(staging);
            log.info("Extração Completa: Validação SEFAZ OK.");

            // Fila SQS enviaria para o Match Engine. Chamamos direto na simulação
            matchEngineService.processEngine(staging, "PC-990432");

        } catch (Exception e) {
            log.error("Erro no pipeline de processamento", e);
        }
    }
}
