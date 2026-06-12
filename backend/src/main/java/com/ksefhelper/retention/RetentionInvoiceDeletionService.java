package com.ksefhelper.retention;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.files.FileStorageService;
import com.ksefhelper.files.entity.StoredFile;
import com.ksefhelper.files.repository.StoredFileRepository;
import com.ksefhelper.invoices.entity.Invoice;
import com.ksefhelper.invoices.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class RetentionInvoiceDeletionService {
    private final InvoiceRepository invoiceRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final AuditEventService auditEventService;

    public RetentionInvoiceDeletionService(
            InvoiceRepository invoiceRepository,
            StoredFileRepository storedFileRepository,
            FileStorageService fileStorageService,
            AuditEventService auditEventService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.auditEventService = auditEventService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return;
        }
        StoredFile file = invoice.getFile();
        auditEventService.recordSystem(
                AuditEventType.INVOICE_RETENTION_DELETED,
                invoice.getOrganization().getId(),
                "invoice",
                invoice.getId(),
                Map.of(
                        "invoiceNumber", invoice.getInvoiceNumber() == null ? "" : invoice.getInvoiceNumber(),
                        "storageKey", file.getStoragePath()
                )
        );
        fileStorageService.scheduleDeletion(file);
        invoiceRepository.delete(invoice);
        storedFileRepository.delete(file);
    }
}
