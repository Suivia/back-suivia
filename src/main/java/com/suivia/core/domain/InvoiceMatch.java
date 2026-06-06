package com.suivia.core.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "invoice_match")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoiceMatch {
    @Id private String id;

    @OneToOne
    @JoinColumn(name = "staging_id")
    private InvoiceStaging staging;

    private String purchaseOrderId;
    private Integer matchScore;
    private String status; // APPROVED, DIVERGENT, REJECTED

    // Mapeado simplificado para o banco, ideal: jsonb
    private String headerMatch;
    private String itemsMatch;

    private String approvedBy;
    private String approvalNote;
    private LocalDateTime approvedAt;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL)
    private List<InvoiceDivergence> divergences;
}
