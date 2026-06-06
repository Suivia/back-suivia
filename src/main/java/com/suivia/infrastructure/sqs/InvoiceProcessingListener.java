package com.suivia.infrastructure.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suivia.invoice.service.InvoiceParserService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Worker: escuta a fila SQS suivia-match-queue.
 * Quando chega uma mensagem de nova NF, dispara o parser.
 * Alinhado com RF02 — Processamento em Lote (Batch).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceProcessingListener {

    private final ObjectMapper objectMapper;
    private final InvoiceParserService parserService;

    @SqsListener("${suivia.aws.sqs-queue}")
    public void listen(String message) {
        log.info("Mensagem recebida da fila SQS: {}", message);

        try {
            Map<String, Object> payload = objectMapper.readValue(message, Map.class);

            String invoiceId = (String) payload.get("invoiceId");
            String fileUrl   = (String) payload.get("fileUrl");
            String fileType  = (String) payload.get("fileType");
            String batchId   = (String) payload.get("batchId");

            log.info("Processando NF | invoiceId={} | batchId={} | tipo={}", invoiceId, batchId, fileType);

            // Processa conforme tipo do arquivo
            if ("xml".equalsIgnoreCase(fileType)) {
                // RF04 — Parser XML Nativo (mais barato e mais rápido que Textract)
                parserService.processXml(invoiceId, fileUrl);
            } else {
                // RF03 — Para PDF/Imagem: será implementado no MVP03 com AWS Textract
                log.warn("Tipo '{}' ainda não suportado neste MVP. invoiceId={}", fileType, invoiceId);
            }

        } catch (Exception e) {
            log.error("Erro ao processar mensagem da fila SQS: {}", e.getMessage(), e);
            // Não relança para não voltar à fila indefinidamente
            // Na Fase 2, implementar DLQ (Dead Letter Queue) aqui
        }
    }
}
