package com.ksefhelper.retention;

import com.ksefhelper.invoices.repository.InvoiceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class RetentionCleanupService {
    private final InvoiceRepository invoiceRepository;
    private final RetentionInvoiceDeletionService deletionService;
    private final int retentionDays;

    public RetentionCleanupService(
            InvoiceRepository invoiceRepository,
            RetentionInvoiceDeletionService deletionService,
            @Value("${app.data.retention.invoice-days:0}") int retentionDays
    ) {
        this.invoiceRepository = invoiceRepository;
        this.deletionService = deletionService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${app.data.retention.poll-interval:1h}")
    public void processExpired() {
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        invoiceRepository.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(cutoff)
                .forEach(invoice -> deletionService.delete(invoice.getId()));
    }
}
