package com.suivia.invoice.controller;

import com.suivia.invoice.dto.InvoiceUploadResponse;
import com.suivia.invoice.service.InvoiceUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceUploadController {

    private final InvoiceUploadService invoiceUploadService;

    /**
     * RF01 — Captura Multicanal (Upload manual)
     * POST /api/invoices/upload
     * Aceita: XML, PDF, JPG, PNG
     */
    @PostMapping("/upload")
    public ResponseEntity<InvoiceUploadResponse> uploadInvoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "batchId", required = false) String batchId) {

        log.info("Recebendo upload de nota fiscal: {} | tamanho: {} bytes",
                file.getOriginalFilename(), file.getSize());

        InvoiceUploadResponse response = invoiceUploadService.processUpload(file, batchId);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * RF01 — Upload em lote (múltiplos arquivos)
     * POST /api/invoices/upload/batch
     */
    @PostMapping("/upload/batch")
    public ResponseEntity<List<InvoiceUploadResponse>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "batchId", required = false) String batchId) {

        log.info("Recebendo lote de {} notas fiscais", files.size());

        List<InvoiceUploadResponse> responses = files.stream()
                .map(file -> invoiceUploadService.processUpload(file, batchId))
                .toList();

        return ResponseEntity.accepted().body(responses);
    }

    /**
     * Health check do módulo de invoice
     * GET /api/invoices/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SUIVIA Invoice Module - OK");
    }
}
