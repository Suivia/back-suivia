package com.suivia.invoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suivia.infrastructure.aws.S3FileReaderService;
import com.suivia.invoice.domain.InvoiceStaging;
import com.suivia.invoice.dto.NFeExtractedData;
import com.suivia.invoice.repository.InvoiceStagingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Serviço de parsing: orquestra a leitura do S3,
 * o parsing do XML e a atualização do staging no banco.
 * Alinhado com RF04 — Parser XML Nativo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceParserService {

    private final S3FileReaderService s3FileReaderService;
    private final NFeXmlParserService nFeXmlParserService;
    private final InvoiceStagingRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Fluxo completo de parsing:
     * 1. Lê o arquivo XML do S3
     * 2. Parseia o XML (extrai todos os campos NFe)
     * 3. Atualiza o staging com os dados extraídos
     * 4. Muda status para EXTRACTED
     */
    public void processXml(String invoiceId, String fileUrl) {
        log.info("Iniciando parsing XML | invoiceId={} | fileUrl={}", invoiceId, fileUrl);

        InvoiceStaging staging = repository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice não encontrada no staging: " + invoiceId));

        try {
            // 1. Lê o arquivo direto do S3
            InputStream xmlStream = s3FileReaderService.readFile(fileUrl);

            // 2. Parseia o XML NFe
            NFeExtractedData extracted = nFeXmlParserService.parse(xmlStream);

            // 3. Atualiza o staging com os dados extraídos
            staging.setSupplierCnpj(extracted.getSupplierCnpj());
            staging.setInvoiceNumber(extracted.getInvoiceNumber());
            staging.setInvoiceKey(extracted.getInvoiceKey());
            staging.setTotalAmount(extracted.getTotalAmount());

            // Serializa itens extraídos como JSON
            if (extracted.getExtractedItems() != null) {
                String itemsJson = objectMapper.writeValueAsString(extracted.getExtractedItems());
                staging.setExtractedItemsJson(itemsJson);
            }

            // 4. Atualiza status para EXTRACTED
            staging.setStatus(InvoiceStaging.InvoiceStagingStatus.extracted);

            repository.save(staging);

            log.info("Parsing concluído com sucesso | invoiceId={} | NF={} | CNPJ={} | Valor={}",
                    invoiceId,
                    extracted.getInvoiceNumber(),
                    extracted.getSupplierCnpj(),
                    extracted.getTotalAmount());

        } catch (Exception e) {
            log.error("Erro no parsing XML | invoiceId={} | erro={}", invoiceId, e.getMessage(), e);

            // Atualiza status para ERROR no banco
            staging.setStatus(InvoiceStaging.InvoiceStagingStatus.error);
            repository.save(staging);

            throw new RuntimeException("Falha no processamento da nota: " + invoiceId, e);
        }
    }
}
