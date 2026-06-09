package com.suivia.repository;
import com.suivia.domain.entity.InvoiceStaging;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

public interface InvoiceStagingRepository extends JpaRepository<InvoiceStaging, UUID> {
    Optional<InvoiceStaging> findByInvoiceKey(String invoiceKey);
}
