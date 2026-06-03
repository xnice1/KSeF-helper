package com.ksefhelper.reports;

import com.ksefhelper.reports.dto.MonthlyReportResponse;
import com.ksefhelper.reports.dto.ValidationReportResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/invoices/{id}/validation-report")
    public ValidationReportResponse validationReport(@PathVariable UUID id) {
        return reportService.validationReport(id);
    }

    @GetMapping("/monthly")
    public MonthlyReportResponse monthlyReport(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        YearMonth period = year == null || month == null ? YearMonth.now() : YearMonth.of(year, month);
        return reportService.monthlyReport(period);
    }
}
