package com.ksefhelper.validation.repository;

import com.ksefhelper.validation.entity.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {
    Optional<ValidationResult> findByInvoiceIdAndInvoiceOrganizationId(UUID invoiceId, UUID organizationId);

    List<ValidationResult> findAllByInvoiceOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
