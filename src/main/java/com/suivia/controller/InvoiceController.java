package com.suivia.controller;

import com.suivia.domain.entity.InvoiceMatch;
import com.suivia.domain.entity.InvoiceStaging;
import com.suivia.repository.InvoiceMatchRepository;
import com.suivia.service.IngestionPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Permite integração do frontend
public class InvoiceController {

    private final IngestionPipelineService ingestionService;
    private final InvoiceMatchRepository matchRepository;

    @PostMapping("/upload")
    public ResponseEntity<InvoiceStaging> uploadInvoice(
            @RequestParam String url,
            @RequestParam(defaultValue = "upload") String source,
            @RequestParam(defaultValue = "pdf") String type) {

        InvoiceStaging staging = ingestionService.ingestFile(url, source, type);
        return ResponseEntity.ok(staging);
    }

    @GetMapping("/inbox")
    public ResponseEntity<List<InvoiceMatch>> getInbox() {
        return ResponseEntity.ok(matchRepository.findAllByOrderByStagingCreatedAtDesc());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> manualApproval(
            @PathVariable java.util.UUID id,
            @RequestParam String user,
            @RequestParam(required = false) String note) {
        // Implementar regra de RN06 (Aprovação com ressalva)
        return ResponseEntity.ok().body("Aprovado com sucesso.");
    }
}
