package com.suivia.domain.entity;

import com.suivia.domain.enums.MatchStatus;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoice_match")
@Data
public class InvoiceMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "staging_id")
    private InvoiceStaging staging;

    private String purchaseOrderId;
    private Integer matchScore;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String headerMatch;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String itemsMatch;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private String taxMatch;

    @Enumerated(EnumType.STRING)
    private MatchStatus status;

    private String approvedBy;
    private String approvalNote;
    private LocalDateTime approvedAt;
}
