package com.ksefhelper.organizations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.audit.dto.AuditEventResponse;
import com.ksefhelper.audit.entity.AuditEvent;
import com.ksefhelper.audit.repository.AuditEventRepository;
import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.companies.entity.Company;
import com.ksefhelper.companies.repository.CompanyRepository;
import com.ksefhelper.files.FileStorageService;
import com.ksefhelper.invoices.entity.Invoice;
import com.ksefhelper.invoices.entity.InvoiceItem;
import com.ksefhelper.invoices.repository.InvoiceRepository;
import com.ksefhelper.organizations.entity.Membership;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.repository.MembershipRepository;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.validation.entity.ValidationMessage;
import com.ksefhelper.validation.entity.ValidationResult;
import com.ksefhelper.validation.repository.ValidationResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class OrganizationDataService {
    private final CurrentUserService currentUserService;
    private final OrganizationAuthorizationService authorizationService;
    private final MembershipRepository membershipRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceRepository invoiceRepository;
    private final ValidationResultRepository validationResultRepository;
    private final AuditEventRepository auditEventRepository;
    private final FileStorageService fileStorageService;
    private final DataDeletionService dataDeletionService;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    public OrganizationDataService(
            CurrentUserService currentUserService,
            OrganizationAuthorizationService authorizationService,
            MembershipRepository membershipRepository,
            CompanyRepository companyRepository,
            InvoiceRepository invoiceRepository,
            ValidationResultRepository validationResultRepository,
            AuditEventRepository auditEventRepository,
            FileStorageService fileStorageService,
            DataDeletionService dataDeletionService,
            AuditEventService auditEventService,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
        this.companyRepository = companyRepository;
        this.invoiceRepository = invoiceRepository;
        this.validationResultRepository = validationResultRepository;
        this.auditEventRepository = auditEventRepository;
        this.fileStorageService = fileStorageService;
        this.dataDeletionService = dataDeletionService;
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> auditEvents() {
        authorizationService.require(OrganizationPermission.VIEW_AUDIT_EVENTS);
        return auditEventRepository.findTop200ByOrganizationIdOrderByOccurredAtDesc(
                        currentUserService.currentOrganizationId()
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ExportedOrganizationData export() {
        authorizationService.require(OrganizationPermission.EXPORT_ORGANIZATION_DATA);
        Organization organization = currentUserService.currentOrganization();
        UUID organizationId = organization.getId();
        List<Membership> memberships = membershipRepository.findAllByOrganizationId(organizationId);
        List<Company> companies = companyRepository.findAllByOrganizationIdOrderByNameAsc(organizationId);
        List<Invoice> invoices = invoiceRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId);
        Map<UUID, ValidationResult> validations = validationResultRepository
                .findAllByInvoiceOrganizationIdOrderByCreatedAtAsc(organizationId)
                .stream()
                .collect(Collectors.toMap(result -> result.getInvoice().getId(), Function.identity()));
        List<AuditEvent> auditEvents = auditEventRepository.findAllByOrganizationIdOrderByOccurredAtAsc(organizationId);

        byte[] bytes = zip(organization, memberships, companies, invoices, validations, auditEvents);
        auditEventService.record(
                AuditEventType.ORGANIZATION_DATA_EXPORTED,
                organizationId,
                "organization",
                organizationId,
                Map.of("invoiceCount", invoices.size(), "fileCount", invoices.size())
        );
        return new ExportedOrganizationData(
                "ksef-helper-" + safeName(organization.getName()) + "-export.zip",
                bytes
        );
    }

    @Transactional
    public void deleteCurrentOrganization(String password, String confirmation) {
        authorizationService.require(OrganizationPermission.DELETE_ORGANIZATION);
        Organization organization = currentUserService.currentOrganization();
        if (!organization.getName().equals(confirmation)) {
            throw new BadRequestException("Enter the exact organization name to confirm permanent deletion.");
        }
        var user = currentUserService.currentUser();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect.");
        }
        dataDeletionService.deleteOrganization(organization, user, "owner_request");
    }

    private byte[] zip(
            Organization organization,
            List<Membership> memberships,
            List<Company> companies,
            List<Invoice> invoices,
            Map<UUID, ValidationResult> validations,
            List<AuditEvent> auditEvents
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                writeJson(zip, "manifest.json", Map.of(
                        "format", "ksef-helper-organization-export",
                        "version", 1,
                        "generatedAt", Instant.now(),
                        "organizationId", organization.getId(),
                        "invoiceCount", invoices.size()
                ));
                writeJson(zip, "organization.json", organizationData(organization));
                writeJson(zip, "members.json", memberships.stream().map(this::membershipData).toList());
                writeJson(zip, "companies.json", companies.stream().map(this::companyData).toList());
                writeJson(zip, "invoices.json", invoices.stream()
                        .map(invoice -> invoiceData(invoice, validations.get(invoice.getId())))
                        .toList());
                writeJson(zip, "audit-events.json", auditEvents.stream().map(this::toResponse).toList());
                for (Invoice invoice : invoices) {
                    String filename = "files/" + invoice.getId() + "-" + safeName(invoice.getFile().getOriginalFilename());
                    write(zip, filename, fileStorageService.exportForBackup(invoice.getFile()));
                }
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Organization data export could not be created.", ex);
        }
    }

    private Map<String, Object> organizationData(Organization organization) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", organization.getId());
        data.put("name", organization.getName());
        data.put("type", organization.getType());
        data.put("createdAt", organization.getCreatedAt());
        data.put("updatedAt", organization.getUpdatedAt());
        return data;
    }

    private Map<String, Object> membershipData(Membership membership) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", membership.getId());
        data.put("userId", membership.getUser().getId());
        data.put("email", membership.getUser().getEmail());
        data.put("firstName", membership.getUser().getFirstName());
        data.put("lastName", membership.getUser().getLastName());
        data.put("role", membership.getRole());
        data.put("createdAt", membership.getCreatedAt());
        return data;
    }

    private Map<String, Object> companyData(Company company) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", company.getId());
        data.put("name", company.getName());
        data.put("nip", company.getNip());
        data.put("regon", company.getRegon());
        data.put("street", company.getStreet());
        data.put("city", company.getCity());
        data.put("postalCode", company.getPostalCode());
        data.put("country", company.getCountry());
        data.put("createdAt", company.getCreatedAt());
        data.put("updatedAt", company.getUpdatedAt());
        return data;
    }

    private Map<String, Object> invoiceData(Invoice invoice, ValidationResult validation) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", invoice.getId());
        data.put("companyId", invoice.getCompany() == null ? null : invoice.getCompany().getId());
        data.put("invoiceNumber", invoice.getInvoiceNumber());
        data.put("sellerName", invoice.getSellerName());
        data.put("sellerNip", invoice.getSellerNip());
        data.put("buyerName", invoice.getBuyerName());
        data.put("buyerNip", invoice.getBuyerNip());
        data.put("issueDate", invoice.getIssueDate());
        data.put("saleDate", invoice.getSaleDate());
        data.put("currency", invoice.getCurrency());
        data.put("netAmount", invoice.getNetAmount());
        data.put("vatAmount", invoice.getVatAmount());
        data.put("grossAmount", invoice.getGrossAmount());
        data.put("paymentMethod", invoice.getPaymentMethod());
        data.put("bankAccount", invoice.getBankAccount());
        data.put("status", invoice.getStatus());
        data.put("createdAt", invoice.getCreatedAt());
        data.put("items", invoice.getItems().stream().map(this::itemData).toList());
        data.put("file", Map.of(
                "id", invoice.getFile().getId(),
                "originalFilename", invoice.getFile().getOriginalFilename(),
                "contentType", invoice.getFile().getContentType(),
                "sizeBytes", invoice.getFile().getSizeBytes(),
                "checksum", invoice.getFile().getChecksum()
        ));
        data.put("validation", validation == null ? null : validationData(validation));
        return data;
    }

    private Map<String, Object> itemData(InvoiceItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.getId());
        data.put("name", item.getName());
        data.put("quantity", item.getQuantity());
        data.put("unitPrice", item.getUnitPrice());
        data.put("netAmount", item.getNetAmount());
        data.put("vatRate", item.getVatRate());
        data.put("vatAmount", item.getVatAmount());
        data.put("grossAmount", item.getGrossAmount());
        return data;
    }

    private Map<String, Object> validationData(ValidationResult result) {
        return Map.of(
                "status", result.getStatus(),
                "createdAt", result.getCreatedAt(),
                "messages", result.getMessages().stream().map(this::validationMessageData).toList()
        );
    }

    private Map<String, Object> validationMessageData(ValidationMessage message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("severity", message.getSeverity());
        data.put("code", message.getCode());
        data.put("fieldPath", message.getFieldPath());
        data.put("message", message.getMessage());
        data.put("suggestion", message.getSuggestion());
        return data;
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getActorUserId(),
                event.getActorEmail(),
                event.getOrganizationId(),
                event.getEventType(),
                event.getTargetType(),
                event.getTargetId(),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getMetadata()
        );
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        write(zip, name, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private void write(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private String safeName(String value) {
        String cleaned = value == null ? "data" : value.replaceAll("[^A-Za-z0-9._-]+", "-");
        return cleaned.isBlank() ? "data" : cleaned;
    }

    public record ExportedOrganizationData(String filename, byte[] bytes) {
    }
}
