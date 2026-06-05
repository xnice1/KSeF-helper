package com.ksefhelper.validation;

import com.ksefhelper.validation.dto.ParsedInvoice;
import com.ksefhelper.validation.dto.ParsedInvoiceItem;
import com.ksefhelper.validation.dto.ValidationIssue;
import com.ksefhelper.validation.entity.ValidationSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessValidationServiceTest {
    private final BusinessValidationService service = new BusinessValidationService();

    @Test
    void returnsNoIssuesForConsistentInvoice() {
        ParsedInvoice invoice = invoice(new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"));

        List<ValidationIssue> issues = service.validate(invoice);

        assertThat(issues).isEmpty();
    }

    @Test
    void returnsErrorWhenTotalsDoNotMatch() {
        ParsedInvoice invoice = invoice(new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("130.00"));

        List<ValidationIssue> issues = service.validate(invoice);

        assertThat(issues)
                .anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR
                        && issue.code().equals("TOTALS_DO_NOT_MATCH"));
    }

    @Test
    void warnsWhenTransferPaymentHasNoBankAccount() {
        ParsedInvoice invoice = new ParsedInvoice(
                "FV/1",
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 15),
                "Seller",
                "5250000000",
                "Buyer",
                "5210000000",
                "PLN",
                new BigDecimal("100.00"),
                new BigDecimal("23.00"),
                new BigDecimal("123.00"),
                "przelew",
                null,
                List.of(item(new BigDecimal("23"), new BigDecimal("23.00"), new BigDecimal("123.00")))
        );

        List<ValidationIssue> issues = service.validate(invoice);

        assertThat(issues)
                .anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING
                        && issue.code().equals("BANK_ACCOUNT_MISSING"));
    }

    @Test
    void warnsAboutUnusualVatRate() {
        ParsedInvoice invoice = new ParsedInvoice(
                "FV/1",
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 15),
                "Seller",
                "5250000000",
                "Buyer",
                "5210000000",
                "PLN",
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("110.00"),
                "card",
                null,
                List.of(item(new BigDecimal("10"), null, new BigDecimal("110.00")))
        );

        List<ValidationIssue> issues = service.validate(invoice);

        assertThat(issues)
                .anyMatch(issue -> issue.code().equals("ITEM_VAT_RATE_UNUSUAL"));
    }

    private ParsedInvoice invoice(BigDecimal net, BigDecimal vat, BigDecimal gross) {
        return new ParsedInvoice(
                "FV/1",
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 15),
                "Seller",
                "5250000000",
                "Buyer",
                "5210000000",
                "PLN",
                net,
                vat,
                gross,
                "TRANSFER",
                "12105000997603123456789123",
                List.of(new ParsedInvoiceItem("Service", BigDecimal.ONE, net, net, new BigDecimal("23"), vat, gross))
        );
    }

    private ParsedInvoiceItem item(BigDecimal vatRate, BigDecimal vatAmount, BigDecimal grossAmount) {
        return new ParsedInvoiceItem(
                "Service",
                BigDecimal.ONE,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                vatRate,
                vatAmount,
                grossAmount
        );
    }
}
