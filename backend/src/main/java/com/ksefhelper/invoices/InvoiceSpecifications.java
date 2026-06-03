package com.ksefhelper.invoices;

import com.ksefhelper.invoices.dto.InvoiceFilterRequest;
import com.ksefhelper.invoices.entity.Invoice;
import org.springframework.data.jpa.domain.Specification;

import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

public final class InvoiceSpecifications {
    private InvoiceSpecifications() {
    }

    public static Specification<Invoice> filtered(UUID organizationId, InvoiceFilterRequest filter) {
        return Specification.where(organization(organizationId))
                .and(containsIgnoreCase("invoiceNumber", filter.invoiceNumber()))
                .and(equalsText("sellerNip", filter.sellerNip()))
                .and(equalsText("buyerNip", filter.buyerNip()))
                .and(filter.companyId() == null ? null : (root, query, cb) -> cb.equal(root.get("company").get("id"), filter.companyId()))
                .and(currency(filter.currency()))
                .and(filter.status() == null ? null : (root, query, cb) -> cb.equal(root.get("status"), filter.status()))
                .and(filter.dateFrom() == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("issueDate"), filter.dateFrom()))
                .and(filter.dateTo() == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("issueDate"), filter.dateTo()))
                .and(filter.uploadedFrom() == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), filter.uploadedFrom().atStartOfDay(ZoneOffset.UTC).toInstant()))
                .and(filter.uploadedTo() == null ? null : (root, query, cb) -> cb.lessThan(root.get("createdAt"), filter.uploadedTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
                .and(filter.minGrossAmount() == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("grossAmount"), filter.minGrossAmount()))
                .and(filter.maxGrossAmount() == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("grossAmount"), filter.maxGrossAmount()));
    }

    private static Specification<Invoice> organization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    private static Specification<Invoice> containsIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
    }

    private static Specification<Invoice> equalsText(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", "");
        return (root, query, cb) -> cb.equal(root.get(field), normalized);
    }

    private static Specification<Invoice> currency(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(root.get("currency"), normalized);
    }
}
