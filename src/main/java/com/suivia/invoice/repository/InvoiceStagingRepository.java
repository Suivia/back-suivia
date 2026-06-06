package com.suivia.invoice.repository;

import com.suivia.invoice.domain.InvoiceStaging;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceStagingRepository extends JpaRepository<InvoiceStaging, String> {

    // Busca por chave de acesso (RN02 - Bloqueio de duplicidade)
    Optional<InvoiceStaging> findByInvoiceKey(String invoiceKey);

    // Busca todas as notas de um lote
    List<InvoiceStaging> findByBatchId(String batchId);

    // Busca por status
    List<InvoiceStaging> findByStatus(InvoiceStaging.InvoiceStagingStatus status);
}
