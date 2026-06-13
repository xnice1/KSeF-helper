package com.ksefhelper.retention;

import com.ksefhelper.invoices.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class RetentionCleanupService {
    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupService.class);
    private static final long ADVISORY_LOCK_ID = 4_976_531_020L;

    private final InvoiceRepository invoiceRepository;
    private final RetentionInvoiceDeletionService deletionService;
    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;

    public RetentionCleanupService(
            InvoiceRepository invoiceRepository,
            RetentionInvoiceDeletionService deletionService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.data.retention.invoice-days:0}") int retentionDays
    ) {
        this.invoiceRepository = invoiceRepository;
        this.deletionService = deletionService;
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${app.data.retention.poll-interval:1h}")
    @Transactional
    public void processExpired() {
        if (retentionDays <= 0) {
            return;
        }
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?)",
                Boolean.class,
                ADVISORY_LOCK_ID
        );
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        invoiceRepository.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(cutoff)
                .stream()
                .map(invoice -> invoice.getId())
                .forEach(this::deleteSafely);
    }

    private void deleteSafely(UUID invoiceId) {
        try {
            deletionService.delete(invoiceId);
        } catch (RuntimeException ex) {
            log.error("Retention deletion failed invoiceId={}", invoiceId, ex);
        }
    }
}
