package com.suivia.core.repository;
import com.suivia.core.domain.InvoiceMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface InvoiceMatchRepository extends JpaRepository<InvoiceMatch, String> {
    List<InvoiceMatch> findByStatus(String status);
    long countByStatus(String status);
}
