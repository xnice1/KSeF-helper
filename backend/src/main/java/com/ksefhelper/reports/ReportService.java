package com.ksefhelper.reports;

import com.ksefhelper.common.exception.NotFoundException;
import com.ksefhelper.invoices.InvoiceMapper;
import com.ksefhelper.invoices.InvoiceService;
import com.ksefhelper.invoices.InvoiceSpecifications;
import com.ksefhelper.invoices.dto.InvoiceFilterRequest;
import com.ksefhelper.invoices.dto.ValidationMessageResponse;
import com.ksefhelper.invoices.entity.Invoice;
import com.ksefhelper.invoices.entity.InvoiceStatus;
import com.ksefhelper.invoices.repository.InvoiceRepository;
import com.ksefhelper.reports.dto.MonthlyReportResponse;
import com.ksefhelper.reports.dto.ValidationReportResponse;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.validation.entity.ValidationSeverity;
import com.ksefhelper.validation.entity.ValidationResult;
import com.ksefhelper.validation.repository.ValidationResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private final InvoiceService invoiceService;
    private final ValidationResultRepository validationResultRepository;
    private final CurrentUserService currentUserService;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceRepository invoiceRepository;

    public ReportService(
            InvoiceService invoiceService,
            ValidationResultRepository validationResultRepository,
            CurrentUserService currentUserService,
            InvoiceMapper invoiceMapper,
            InvoiceRepository invoiceRepository
    ) {
        this.invoiceService = invoiceService;
        this.validationResultRepository = validationResultRepository;
        this.currentUserService = currentUserService;
        this.invoiceMapper = invoiceMapper;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public ValidationReportResponse validationReport(UUID invoiceId) {
        var invoice = invoiceService.findScoped(invoiceId);
        ValidationResult result = validationResultRepository.findByInvoiceIdAndInvoiceOrganizationId(invoiceId, currentUserService.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Validation result was not found."));
        List<ValidationMessageResponse> messages = result.getMessages().stream()
                .map(invoiceMapper::toValidationMessage)
                .toList();
        List<ValidationMessageResponse> errors = messages.stream()
                .filter(message -> message.severity() == ValidationSeverity.ERROR)
                .toList();
        List<ValidationMessageResponse> warnings = messages.stream()
                .filter(message -> message.severity() == ValidationSeverity.WARNING)
                .toList();
        List<String> suggestions = messages.stream()
                .map(ValidationMessageResponse::suggestion)
                .filter(suggestion -> suggestion != null && !suggestion.isBlank())
                .distinct()
                .toList();

        return new ValidationReportResponse(
                invoiceMapper.toSummary(invoice),
                result.getStatus(),
                errors,
                warnings,
                suggestions,
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse monthlyReport(YearMonth period) {
        UUID organizationId = currentUserService.currentOrganizationId();
        InvoiceFilterRequest filter = new InvoiceFilterRequest(
                null,
                null,
                null,
                null,
                null,
                period.atDay(1),
                period.atEndOfMonth(),
                null,
                null,
                null,
                null,
                null
        );
        List<Invoice> invoices = invoiceRepository.findAll(InvoiceSpecifications.filtered(organizationId, filter));

        Map<InvoiceStatus, Long> statusCounts = new EnumMap<>(InvoiceStatus.class);
        for (InvoiceStatus status : InvoiceStatus.values()) {
            statusCounts.put(status, invoices.stream().filter(invoice -> invoice.getStatus() == status).count());
        }

        List<MonthlyReportResponse.CurrencyTotal> totals = invoices.stream()
                .collect(Collectors.groupingBy(invoice -> invoice.getCurrency() == null ? "UNKNOWN" : invoice.getCurrency()))
                .entrySet()
                .stream()
                .map(entry -> new MonthlyReportResponse.CurrencyTotal(
                        entry.getKey(),
                        entry.getValue().size(),
                        sum(entry.getValue(), AmountType.NET),
                        sum(entry.getValue(), AmountType.VAT),
                        sum(entry.getValue(), AmountType.GROSS)
                ))
                .toList();

        return new MonthlyReportResponse(period.getYear(), period.getMonthValue(), statusCounts, totals, Instant.now());
    }

    private BigDecimal sum(List<Invoice> invoices, AmountType amountType) {
        return invoices.stream()
                .map(invoice -> switch (amountType) {
                    case NET -> invoice.getNetAmount();
                    case VAT -> invoice.getVatAmount();
                    case GROSS -> invoice.getGrossAmount();
                })
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private enum AmountType {
        NET,
        VAT,
        GROSS
    }
}
