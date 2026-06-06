package com.suivia.core.domain;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_divergences")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoiceDivergence {
    @Id private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private InvoiceMatch match;

    private String divergenceType;
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    private String fieldName;
    private String expectedValue;
    private String actualValue;
    private BigDecimal differenceValue;
    private Boolean toleranceApplied;
    private String resolution; // PENDING, AUTO_APPROVED, MANUALLY_APPROVED
}
