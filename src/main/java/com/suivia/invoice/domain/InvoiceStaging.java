package com.suivia.invoice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade de Staging: representa uma Nota Fiscal recebida e ainda
 * em processamento. Nenhum dado desta tabela impacta financeiro até aprovação.
 * Retenção: 90 dias (conforme RF06).
 */
@Entity
@Table(name = "invoice_staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceStaging {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;  // UUID gerado na entrada

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private InvoiceSource source;  // upload | email | api | camera | s3

    @Column(name = "file_url")
    private String fileUrl;  // URL no S3

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type")
    private FileType fileType;  // xml | pdf | jpg | png

    @Column(name = "supplier_cnpj", length = 18)
    private String supplierCnpj;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_key", length = 44, unique = true)
    private String invoiceKey;  // Chave de acesso NFe (44 dígitos)

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "sefaz_status")
    private SefazStatus sefazStatus;  // authorized | cancelled | denied | pending

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStagingStatus status;  // processing | extracted | matched | error | rejected

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // ── Enums internos ──────────────────────────────────────────────

    public enum InvoiceSource { upload, email, api, camera, s3 }

    public enum FileType { xml, pdf, jpg, png }

    public enum SefazStatus { authorized, cancelled, denied, pending }

    public enum InvoiceStagingStatus { processing, extracted, matched, error, rejected }
}
