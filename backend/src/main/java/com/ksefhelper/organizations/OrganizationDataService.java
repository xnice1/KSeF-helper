package com.ksefhelper.organizations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.audit.AuditEventResponses;
import com.ksefhelper.audit.dto.AuditEventResponse;
import com.ksefhelper.audit.entity.AuditEvent;
import com.ksefhelper.audit.repository.AuditEventRepository;
import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.companies.entity.Company;
import com.ksefhelper.companies.repository.CompanyRepository;
import com.ksefhelper.files.FileStorageService;
import com.ksefhelper.files.repository.StoredFileRepository;
import com.ksefhelper.invoices.entity.Invoice;
import com.ksefhelper.invoices.entity.InvoiceItem;
import com.ksefhelper.invoices.repository.InvoiceRepository;
import com.ksefhelper.organizations.entity.Membership;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.repository.MembershipRepository;
import com.ksefhelper.organizations.repository.OrganizationRepository;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.validation.entity.ValidationMessage;
import com.ksefhelper.validation.entity.ValidationResult;
import com.ksefhelper.validation.repository.ValidationResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.persistence.EntityManager;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final OrganizationRepository organizationRepository;
    private final CompanyRepository companyRepository;
    private final InvoiceRepository invoiceRepository;
    private final ValidationResultRepository validationResultRepository;
    private final AuditEventRepository auditEventRepository;
    private final FileStorageService fileStorageService;
    private final StoredFileRepository storedFileRepository;
    private final DataDeletionService dataDeletionService;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final long maxExportBytes;
    private final long maxExportInvoices;
    private final long maxExportRecords;
    private final long maxArchiveBytes;
    private final int exportPageSize;

    public OrganizationDataService(
            CurrentUserService currentUserService,
            OrganizationAuthorizationService authorizationService,
            MembershipRepository membershipRepository,
            OrganizationRepository organizationRepository,
            CompanyRepository companyRepository,
            InvoiceRepository invoiceRepository,
            ValidationResultRepository validationResultRepository,
            AuditEventRepository auditEventRepository,
            FileStorageService fileStorageService,
            StoredFileRepository storedFileRepository,
            DataDeletionService dataDeletionService,
            AuditEventService auditEventService,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate,
            @Value("${app.data.export.max-bytes:1073741824}") long maxExportBytes,
            @Value("${app.data.export.max-invoices:10000}") long maxExportInvoices,
            @Value("${app.data.export.max-records:250000}") long maxExportRecords,
            @Value("${app.data.export.max-archive-bytes:1200000000}") long maxArchiveBytes,
            @Value("${app.data.export.page-size:100}") int exportPageSize
    ) {
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
        this.companyRepository = companyRepository;
        this.invoiceRepository = invoiceRepository;
        this.validationResultRepository = validationResultRepository;
        this.auditEventRepository = auditEventRepository;
        this.fileStorageService = fileStorageService;
        this.storedFileRepository = storedFileRepository;
        this.dataDeletionService = dataDeletionService;
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
        this.maxExportBytes = maxExportBytes;
        this.maxExportInvoices = maxExportInvoices;
        this.maxExportRecords = maxExportRecords;
        this.maxArchiveBytes = maxArchiveBytes;
        this.exportPageSize = exportPageSize;
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

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExportedOrganizationData export() {
        authorizationService.require(OrganizationPermission.EXPORT_ORGANIZATION_DATA);
        Organization organization = currentUserService.currentOrganization();
        UUID organizationId = organization.getId();
        long invoiceCount = invoiceRepository.countByOrganizationId(organizationId);
        long fileBytes = storedFileRepository.totalSizeByOrganizationId(organizationId);
        long databaseRecords = exportRecordCount(organizationId);
        if (invoiceCount > maxExportInvoices
                || fileBytes > maxExportBytes
                || databaseRecords > maxExportRecords) {
            throw new BadRequestException(
                    "This organization is too large for an immediate export. Contact support for an asynchronous export."
            );
        }

        Path path = zip(organization, invoiceCount);
        auditEventService.record(
                AuditEventType.ORGANIZATION_DATA_EXPORTED,
                organizationId,
                "organization",
                organizationId,
                Map.of("invoiceCount", invoiceCount, "fileBytes", fileBytes, "databaseRecords", databaseRecords)
        );
        return new ExportedOrganizationData(
                "ksef-helper-" + safeName(organization.getName()) + "-export.zip",
                path
        );
    }

    @Transactional
    public void deleteCurrentOrganization(String password, String confirmation) {
        Organization organization = currentUserService.currentOrganization();
        organizationRepository.findByIdForUpdate(organization.getId())
                .orElseThrow(() -> new BadRequestException("Organization was not found."));
        authorizationService.require(OrganizationPermission.DELETE_ORGANIZATION);
        if (!organization.getName().equals(confirmation)) {
            throw new BadRequestException("Enter the exact organization name to confirm permanent deletion.");
        }
        var user = currentUserService.currentUser();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect.");
        }
        dataDeletionService.deleteOrganization(organization, user, "owner_request");
    }

    private Path zip(Organization organization, long invoiceCount) {
        Path path = null;
        try {
            path = Files.createTempFile("ksef-helper-export-", ".zip");
            try (OutputStream output = new BoundedOutputStream(Files.newOutputStream(path), maxArchiveBytes);
                 ZipOutputStream zip = new ZipOutputStream(output)) {
                writeJson(zip, "manifest.json", Map.of(
                        "format", "ksef-helper-organization-export",
                        "version", 1,
                        "generatedAt", Instant.now(),
                        "organizationId", organization.getId(),
                        "invoiceCount", invoiceCount
                ));
                writeJson(zip, "organization.json", organizationData(organization));
                writePagedJson(
                        zip,
                        "members.json",
                        pageable -> membershipRepository.findByOrganizationIdOrderById(organization.getId(), pageable),
                        this::membershipData
                );
                writePagedJson(
                        zip,
                        "companies.json",
                        pageable -> companyRepository.findByOrganizationIdOrderById(organization.getId(), pageable),
                        this::companyData
                );
                writeInvoices(zip, organization.getId());
                writePagedJson(
                        zip,
                        "audit-events.json",
                        pageable -> auditEventRepository.findByOrganizationIdOrderById(organization.getId(), pageable),
                        this::toResponse
                );
                writeFiles(zip, organization.getId());
            }
            return path;
        } catch (IOException ex) {
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // The operating system will eventually clear its temporary directory.
                }
            }
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
        return AuditEventResponses.from(event);
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        JsonGenerator generator = objectMapper.getFactory().createGenerator(zip);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(generator, value);
        generator.flush();
        zip.closeEntry();
    }

    private <T> void writePagedJson(
            ZipOutputStream zip,
            String name,
            PageFetcher<T> fetcher,
            Function<T, ?> mapper
    ) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        JsonGenerator generator = objectMapper.getFactory().createGenerator(zip);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        generator.writeStartArray();
        int page = 0;
        boolean hasNext;
        do {
            Slice<T> slice = fetcher.fetch(PageRequest.of(page, exportPageSize));
            for (T value : slice.getContent()) {
                generator.writeObject(mapper.apply(value));
            }
            hasNext = slice.hasNext();
            entityManager.clear();
            page++;
        } while (hasNext);
        generator.writeEndArray();
        generator.flush();
        zip.closeEntry();
    }

    private void writeInvoices(ZipOutputStream zip, UUID organizationId) throws IOException {
        zip.putNextEntry(new ZipEntry("invoices.json"));
        JsonGenerator generator = objectMapper.getFactory().createGenerator(zip);
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        generator.writeStartArray();
        int page = 0;
        boolean hasNext;
        do {
            Slice<Invoice> slice = invoiceRepository.findByOrganizationIdOrderById(
                    organizationId,
                    PageRequest.of(page, exportPageSize)
            );
            List<UUID> invoiceIds = slice.getContent().stream().map(Invoice::getId).toList();
            Map<UUID, ValidationResult> validations = invoiceIds.isEmpty()
                    ? Map.of()
                    : validationResultRepository.findAllByInvoiceIdIn(invoiceIds)
                            .stream()
                            .collect(Collectors.toMap(result -> result.getInvoice().getId(), Function.identity()));
            for (Invoice invoice : slice.getContent()) {
                generator.writeObject(invoiceData(invoice, validations.get(invoice.getId())));
            }
            hasNext = slice.hasNext();
            entityManager.clear();
            page++;
        } while (hasNext);
        generator.writeEndArray();
        generator.flush();
        zip.closeEntry();
    }

    private void writeFiles(ZipOutputStream zip, UUID organizationId) throws IOException {
        int page = 0;
        boolean hasNext;
        do {
            Slice<Invoice> slice = invoiceRepository.findByOrganizationIdOrderById(
                    organizationId,
                    PageRequest.of(page, exportPageSize)
            );
            for (Invoice invoice : slice.getContent()) {
                String filename = "files/" + invoice.getId() + "-" + safeName(invoice.getFile().getOriginalFilename());
                zip.putNextEntry(new ZipEntry(filename));
                fileStorageService.writeForBackup(invoice.getFile(), zip);
                zip.closeEntry();
            }
            hasNext = slice.hasNext();
            entityManager.clear();
            page++;
        } while (hasNext);
    }

    private long exportRecordCount(UUID organizationId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT
                    (SELECT COUNT(*) FROM memberships WHERE organization_id = ?) +
                    (SELECT COUNT(*) FROM companies WHERE organization_id = ?) +
                    (SELECT COUNT(*) FROM stored_files WHERE organization_id = ?) +
                    (SELECT COUNT(*) FROM invoices WHERE organization_id = ?) +
                    (SELECT COUNT(*) FROM invoice_items item
                        JOIN invoices invoice ON invoice.id = item.invoice_id
                        WHERE invoice.organization_id = ?) +
                    (SELECT COUNT(*) FROM validation_results result
                        JOIN invoices invoice ON invoice.id = result.invoice_id
                        WHERE invoice.organization_id = ?) +
                    (SELECT COUNT(*) FROM validation_messages message
                        JOIN validation_results result ON result.id = message.validation_result_id
                        JOIN invoices invoice ON invoice.id = result.invoice_id
                        WHERE invoice.organization_id = ?) +
                    (SELECT COUNT(*) FROM audit_events WHERE organization_id = ?)
                """,
                Long.class,
                organizationId,
                organizationId,
                organizationId,
                organizationId,
                organizationId,
                organizationId,
                organizationId,
                organizationId
        );
        return count == null ? 0 : count;
    }

    private String safeName(String value) {
        String cleaned = value == null ? "data" : value.replaceAll("[^A-Za-z0-9._-]+", "-");
        return cleaned.isBlank() ? "data" : cleaned;
    }

    public record ExportedOrganizationData(String filename, Path path) {
    }

    @FunctionalInterface
    private interface PageFetcher<T> {
        Slice<T> fetch(Pageable pageable);
    }

    private static final class BoundedOutputStream extends FilterOutputStream {
        private final long maximum;
        private long written;

        private BoundedOutputStream(OutputStream output, long maximum) {
            super(output);
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1);
            out.write(value);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            ensureCapacity(length);
            out.write(bytes, offset, length);
            written += length;
        }

        private void ensureCapacity(int additional) throws IOException {
            if (written + additional > maximum) {
                throw new IOException("Organization export exceeded the configured archive size limit.");
            }
        }
    }
}
