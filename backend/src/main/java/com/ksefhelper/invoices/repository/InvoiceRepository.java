package com.ksefhelper.invoices.repository;

import com.ksefhelper.invoices.entity.Invoice;
import com.ksefhelper.invoices.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
    Optional<Invoice> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Invoice> findAllByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    List<Invoice> findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(Instant cutoff);

    long countByOrganizationIdAndStatus(UUID organizationId, InvoiceStatus status);
}
