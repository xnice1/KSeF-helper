package com.ksefhelper.invoices;

import com.ksefhelper.common.exception.NotFoundException;
import com.ksefhelper.companies.CompanyService;
import com.ksefhelper.companies.entity.Company;
import com.ksefhelper.files.FileStorageService;
import com.ksefhelper.files.entity.StoredFile;
import com.ksefhelper.files.repository.StoredFileRepository;
import com.ksefhelper.invoices.dto.InvoiceFilterRequest;
import com.ksefhelper.invoices.dto.InvoiceSummaryResponse;
import com.ksefhelper.invoices.dto.InvoiceValidationResponse;
import com.ksefhelper.invoices.dto.UploadInvoiceResponse;
import com.ksefhelper.invoices.entity.Invoice;
import com.ksefhelper.invoices.entity.InvoiceItem;
import com.ksefhelper.invoices.entity.InvoiceStatus;
import com.ksefhelper.invoices.repository.InvoiceRepository;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.OrganizationAuthorizationService;
import com.ksefhelper.organizations.OrganizationPermission;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.security.ratelimit.RateLimitService;
import com.ksefhelper.validation.BusinessValidationService;
import com.ksefhelper.validation.InvoiceXmlParser;
import com.ksefhelper.validation.XmlTechnicalValidationService;
import com.ksefhelper.validation.dto.ParsedInvoice;
import com.ksefhelper.validation.dto.ParsedInvoiceItem;
import com.ksefhelper.validation.dto.ValidationIssue;
import com.ksefhelper.validation.entity.ValidationMessage;
import com.ksefhelper.validation.entity.ValidationResult;
import com.ksefhelper.validation.entity.ValidationSeverity;
import com.ksefhelper.validation.entity.ValidationStatus;
import com.ksefhelper.validation.repository.ValidationResultRepository;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final ValidationResultRepository validationResultRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;
    private final CompanyService companyService;
    private final XmlTechnicalValidationService technicalValidationService;
    private final InvoiceXmlParser invoiceXmlParser;
    private final BusinessValidationService businessValidationService;
    private final InvoiceMapper invoiceMapper;
    private final OrganizationAuthorizationService authorizationService;
    private final RateLimitService rateLimitService;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            ValidationResultRepository validationResultRepository,
            StoredFileRepository storedFileRepository,
            FileStorageService fileStorageService,
            CurrentUserService currentUserService,
            CompanyService companyService,
            XmlTechnicalValidationService technicalValidationService,
            InvoiceXmlParser invoiceXmlParser,
            BusinessValidationService businessValidationService,
            InvoiceMapper invoiceMapper,
            OrganizationAuthorizationService authorizationService,
            RateLimitService rateLimitService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.validationResultRepository = validationResultRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
        this.companyService = companyService;
        this.technicalValidationService = technicalValidationService;
        this.invoiceXmlParser = invoiceXmlParser;
        this.businessValidationService = businessValidationService;
        this.invoiceMapper = invoiceMapper;
        this.authorizationService = authorizationService;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public UploadInvoiceResponse upload(MultipartFile multipartFile, UUID companyId) {
        authorizationService.require(OrganizationPermission.UPLOAD_INVOICES);
        Organization organization = currentUserService.currentOrganization();
        rateLimitService.checkUpload(currentUserService.currentUser().getId(), organization.getId());
        Company company = companyId == null ? null : companyService.findScoped(companyId);
        StoredFile storedFile = fileStorageService.storeXml(multipartFile, organization);
        File xmlFile = new File(storedFile.getStoragePath());

        ValidationRun validationRun = runValidation(xmlFile);
        List<ValidationIssue> issues = validationRun.issues();
        ParsedInvoice parsedInvoice = validationRun.parsedInvoice();

        InvoiceStatus invoiceStatus = invoiceStatus(issues);
        Invoice invoice = createInvoice(organization, company, storedFile, parsedInvoice, invoiceStatus);
        Invoice savedInvoice = invoiceRepository.save(invoice);
        ValidationResult validationResult = createValidationResult(savedInvoice, issues, validationStatus(invoiceStatus));
        ValidationResult savedValidationResult = validationResultRepository.save(validationResult);

        return new UploadInvoiceResponse(
                savedInvoice.getId(),
                savedInvoice.getStatus(),
                savedInvoice.getInvoiceNumber(),
                savedValidationResult.getMessages().stream().map(invoiceMapper::toValidationMessage).toList()
        );
    }

    @Transactional
    public InvoiceValidationResponse revalidate(UUID id) {
        authorizationService.require(OrganizationPermission.REVALIDATE_INVOICES);
        Invoice invoice = findScoped(id);
        ValidationResult result = validationResultRepository.findByInvoiceIdAndInvoiceOrganizationId(
                        id,
                        currentUserService.currentOrganizationId()
                )
                .orElseThrow(() -> new NotFoundException("Validation result was not found."));

        ValidationRun validationRun = runValidation(new File(invoice.getFile().getStoragePath()));
        InvoiceStatus status = invoiceStatus(validationRun.issues());
        applyParsedInvoice(invoice, validationRun.parsedInvoice());
        invoice.setStatus(status);

        result.setStatus(validationStatus(status));
        result.clearMessages();
        addValidationMessages(result, validationRun.issues());

        invoiceRepository.save(invoice);
        ValidationResult savedResult = validationResultRepository.save(result);
        return new InvoiceValidationResponse(
                invoice.getId(),
                savedResult.getStatus(),
                savedResult.getUpdatedAt(),
                savedResult.getMessages().stream().map(invoiceMapper::toValidationMessage).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummaryResponse> list(InvoiceFilterRequest filter) {
        authorizationService.require(OrganizationPermission.VIEW_INVOICES);
        UUID organizationId = currentUserService.currentOrganizationId();
        return invoiceRepository.findAll(
                        InvoiceSpecifications.filtered(organizationId, filter),
                        Sort.by(Sort.Direction.DESC, "createdAt")
                ).stream()
                .map(invoiceMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceSummaryResponse get(UUID id) {
        authorizationService.require(OrganizationPermission.VIEW_INVOICES);
        return invoiceMapper.toSummary(findScoped(id));
    }

    @Transactional(readOnly = true)
    public com.ksefhelper.invoices.dto.InvoicePreviewResponse preview(UUID id) {
        authorizationService.require(OrganizationPermission.VIEW_INVOICES);
        Invoice invoice = findScoped(id);
        ValidationResult result = validationResultRepository.findByInvoiceIdAndInvoiceOrganizationId(id, currentUserService.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Validation result was not found."));
        return invoiceMapper.toPreview(invoice, result);
    }

    @Transactional(readOnly = true)
    public InvoiceValidationResponse validation(UUID id) {
        authorizationService.require(OrganizationPermission.VIEW_INVOICES);
        ValidationResult result = validationResultRepository.findByInvoiceIdAndInvoiceOrganizationId(id, currentUserService.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Validation result was not found."));
        return new InvoiceValidationResponse(
                result.getInvoice().getId(),
                result.getStatus(),
                result.getCreatedAt(),
                result.getMessages().stream().map(invoiceMapper::toValidationMessage).toList()
        );
    }

    @Transactional(readOnly = true)
    public DownloadedInvoiceFile downloadOriginal(UUID id) {
        authorizationService.require(OrganizationPermission.DOWNLOAD_INVOICES);
        Invoice invoice = findScoped(id);
        Resource resource = fileStorageService.load(invoice.getFile());
        return new DownloadedInvoiceFile(resource, invoice.getFile().getOriginalFilename(), invoice.getFile().getContentType());
    }

    @Transactional
    public void delete(UUID id) {
        authorizationService.require(OrganizationPermission.DELETE_INVOICES);
        Invoice invoice = findScoped(id);
        StoredFile file = invoice.getFile();
        invoiceRepository.delete(invoice);
        storedFileRepository.delete(file);
        fileStorageService.deletePhysicalFile(file);
    }

    public Invoice findScoped(UUID id) {
        authorizationService.require(OrganizationPermission.VIEW_INVOICES);
        return invoiceRepository.findByIdAndOrganizationId(id, currentUserService.currentOrganizationId())
                .orElseThrow(() -> new NotFoundException("Invoice was not found."));
    }

    private Invoice createInvoice(
            Organization organization,
            Company company,
            StoredFile storedFile,
            ParsedInvoice parsedInvoice,
            InvoiceStatus status
    ) {
        Invoice invoice = new Invoice();
        invoice.setOrganization(organization);
        invoice.setCompany(company);
        invoice.setFile(storedFile);
        applyParsedInvoice(invoice, parsedInvoice);
        invoice.setStatus(status);
        return invoice;
    }

    private void applyParsedInvoice(Invoice invoice, ParsedInvoice parsedInvoice) {
        invoice.setInvoiceNumber(parsedInvoice.invoiceNumber());
        invoice.setIssueDate(parsedInvoice.issueDate());
        invoice.setSaleDate(parsedInvoice.saleDate());
        invoice.setSellerName(parsedInvoice.sellerName());
        invoice.setSellerNip(parsedInvoice.sellerNip());
        invoice.setBuyerName(parsedInvoice.buyerName());
        invoice.setBuyerNip(parsedInvoice.buyerNip());
        invoice.setCurrency(parsedInvoice.currency());
        invoice.setNetAmount(parsedInvoice.netAmount());
        invoice.setVatAmount(parsedInvoice.vatAmount());
        invoice.setGrossAmount(parsedInvoice.grossAmount());
        invoice.setPaymentMethod(parsedInvoice.paymentMethod());
        invoice.setBankAccount(parsedInvoice.bankAccount());
        invoice.clearItems();
        for (ParsedInvoiceItem parsedItem : parsedInvoice.items()) {
            InvoiceItem item = new InvoiceItem();
            item.setName(parsedItem.name());
            item.setQuantity(parsedItem.quantity());
            item.setUnitPrice(parsedItem.unitPrice());
            item.setNetAmount(parsedItem.netAmount());
            item.setVatRate(parsedItem.vatRate());
            item.setVatAmount(parsedItem.vatAmount());
            item.setGrossAmount(parsedItem.grossAmount());
            invoice.addItem(item);
        }
    }

    private ValidationResult createValidationResult(Invoice invoice, List<ValidationIssue> issues, ValidationStatus status) {
        ValidationResult result = new ValidationResult();
        result.setInvoice(invoice);
        result.setStatus(status);
        addValidationMessages(result, issues);
        return result;
    }

    private void addValidationMessages(ValidationResult result, List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            ValidationMessage message = new ValidationMessage();
            message.setSeverity(issue.severity());
            message.setCode(issue.code());
            message.setFieldPath(issue.fieldPath());
            message.setMessage(issue.message());
            message.setSuggestion(issue.suggestion());
            result.addMessage(message);
        }
    }

    private ValidationRun runValidation(File xmlFile) {
        List<ValidationIssue> issues = new ArrayList<>(technicalValidationService.validate(xmlFile));
        ParsedInvoice parsedInvoice = ParsedInvoice.empty();
        try {
            parsedInvoice = invoiceXmlParser.parse(xmlFile);
            issues.addAll(businessValidationService.validate(parsedInvoice));
        } catch (IllegalArgumentException ex) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "XML_PARSE_FAILED",
                    null,
                    "The XML file could not be read safely.",
                    "Check that the file is well-formed XML and does not contain unsupported external resources."
            ));
        }
        return new ValidationRun(parsedInvoice, issues);
    }

    private InvoiceStatus invoiceStatus(List<ValidationIssue> issues) {
        boolean hasError = issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
        if (hasError) {
            return InvoiceStatus.INVALID;
        }
        boolean hasWarning = issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING);
        return hasWarning ? InvoiceStatus.WARNING : InvoiceStatus.VALID;
    }

    private ValidationStatus validationStatus(InvoiceStatus invoiceStatus) {
        return switch (invoiceStatus) {
            case VALID -> ValidationStatus.VALID;
            case WARNING -> ValidationStatus.WARNING;
            default -> ValidationStatus.INVALID;
        };
    }

    public record DownloadedInvoiceFile(Resource resource, String originalFilename, String contentType) {
    }

    private record ValidationRun(ParsedInvoice parsedInvoice, List<ValidationIssue> issues) {
    }
}
