package com.suivia.core.repository;
import com.suivia.core.domain.InvoiceStaging;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InvoiceStagingRepository extends JpaRepository<InvoiceStaging, String> {
    long countByStatus(String status);
}
