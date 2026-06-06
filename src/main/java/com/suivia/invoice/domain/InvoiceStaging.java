package com.suivia.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceStaging {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private InvoiceSource source;

    @Column(name = "file_url")
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type")
    private FileType fileType;

    @Column(name = "supplier_cnpj", length = 18)
    private String supplierCnpj;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_key", length = 44, unique = true)
    private String invoiceKey;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    // Itens extraídos serializados como JSON (RF04)
    @Column(name = "extracted_items_json", columnDefinition = "TEXT")
    private String extractedItemsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "sefaz_status")
    private SefazStatus sefazStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStagingStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public enum InvoiceSource   { upload, email, api, camera, s3 }
    public enum FileType        { xml, pdf, jpg, png }
    public enum SefazStatus     { authorized, cancelled, denied, pending }
    public enum InvoiceStagingStatus { processing, extracted, matched, error, rejected }
}
