package com.suivia.api;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceUploadController {

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, @RequestParam("batchId") String batchId) {
        // Integração real com S3 Service aqui. Mock para manter coesão.
        return ResponseEntity.ok(Map.of(
            "invoiceId", UUID.randomUUID().toString(),
            "status", "PROCESSING",
            "message", "XML Recebido. Worker iniciado."
        ));
    }
}
