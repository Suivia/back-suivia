package com.suivia.repository;
import com.suivia.domain.entity.InvoiceMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface InvoiceMatchRepository extends JpaRepository<InvoiceMatch, UUID> {
    List<InvoiceMatch> findAllByOrderByStagingCreatedAtDesc();
}
