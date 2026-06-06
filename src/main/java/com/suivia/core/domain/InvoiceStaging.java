package com.suivia.core.domain;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_staging")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoiceStaging {
    @Id private String id;
    private String batchId;
    private String source;
    private String fileUrl;
    private String fileType;
    private String supplierCnpj;
    private String invoiceNumber;
    private String invoiceKey;
    private BigDecimal totalAmount;
    private String extractedItemsJson;
    private String sefazStatus;
    private String status; // PROCESSING, EXTRACTED, ERROR, REJECTED, MATCHED
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}
