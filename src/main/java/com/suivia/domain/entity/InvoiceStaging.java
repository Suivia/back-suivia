package com.suivia.domain.entity;

import com.suivia.domain.enums.InvoiceStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoice_staging")
@Data
public class InvoiceStaging {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String batchId;
    private String source; // email, upload, api, camera
    private String fileUrl;
    private String fileType; // xml, pdf, jpg

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String rawTextractJson; // Simula armazenamento do JSON do Textract

    private String supplierCnpj;
    private String invoiceNumber;

    @Column(unique = true)
    private String invoiceKey;

    private BigDecimal totalAmount;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String extractedItems;

    private String sefazStatus; // AUTHORIZED, CANCELLED

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
