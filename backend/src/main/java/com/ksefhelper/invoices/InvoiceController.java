package com.ksefhelper.invoices;

import com.ksefhelper.invoices.dto.InvoiceFilterRequest;
import com.ksefhelper.invoices.dto.InvoicePreviewResponse;
import com.ksefhelper.invoices.dto.InvoiceSummaryResponse;
import com.ksefhelper.invoices.dto.InvoiceValidationResponse;
import com.ksefhelper.invoices.dto.UploadInvoiceResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadInvoiceResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "companyId", required = false) UUID companyId
    ) {
        return invoiceService.upload(file, companyId);
    }

    @GetMapping
    public List<InvoiceSummaryResponse> list(@ModelAttribute InvoiceFilterRequest filter) {
        return invoiceService.list(filter);
    }

    @GetMapping("/{id}")
    public InvoiceSummaryResponse get(@PathVariable UUID id) {
        return invoiceService.get(id);
    }

    @GetMapping("/{id}/preview")
    public InvoicePreviewResponse preview(@PathVariable UUID id) {
        return invoiceService.preview(id);
    }

    @GetMapping("/{id}/validation")
    public InvoiceValidationResponse validation(@PathVariable UUID id) {
        return invoiceService.validation(id);
    }

    @GetMapping("/{id}/download-original")
    public ResponseEntity<?> downloadOriginal(@PathVariable UUID id) {
        InvoiceService.DownloadedInvoiceFile file = invoiceService.downloadOriginal(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.originalFilename() + "\"")
                .body(file.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
