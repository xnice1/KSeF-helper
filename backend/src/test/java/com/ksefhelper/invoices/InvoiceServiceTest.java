package com.ksefhelper.invoices;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.files.FileStorageService;
import com.ksefhelper.files.entity.StoredFile;
import com.ksefhelper.invoices.dto.InvoiceValidationResponse;
import com.ksefhelper.invoices.entity.Invoice;
import com.ksefhelper.invoices.entity.InvoiceStatus;
import com.ksefhelper.invoices.repository.InvoiceRepository;
import com.ksefhelper.security.CurrentUserService;
import com.ksefhelper.security.ratelimit.RateLimitService;
import com.ksefhelper.organizations.OrganizationAuthorizationService;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.validation.BusinessValidationService;
import com.ksefhelper.validation.InvoiceXmlParser;
import com.ksefhelper.validation.XmlTechnicalValidationService;
import com.ksefhelper.validation.entity.ValidationMessage;
import com.ksefhelper.validation.entity.ValidationResult;
import com.ksefhelper.validation.entity.ValidationSeverity;
import com.ksefhelper.validation.entity.ValidationStatus;
import com.ksefhelper.validation.repository.ValidationResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceServiceTest {
    @Test
    void revalidationReplacesStoredMessagesAndRefreshesInvoiceData() throws Exception {
        InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
        ValidationResultRepository validationResultRepository = mock(ValidationResultRepository.class);
        UUID invoiceId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        File xmlFile = File.createTempFile("fa3-correction", ".xml");
        Files.writeString(xmlFile.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Faktura xmlns="http://crd.gov.pl/wzor/2025/06/25/13775/">
                    <Podmiot1>
                        <DaneIdentyfikacyjne>
                            <NIP>5250000000</NIP>
                            <Nazwa>Seller</Nazwa>
                        </DaneIdentyfikacyjne>
                    </Podmiot1>
                    <Podmiot2>
                        <DaneIdentyfikacyjne>
                            <NIP>5210000000</NIP>
                            <Nazwa>Buyer</Nazwa>
                        </DaneIdentyfikacyjne>
                    </Podmiot2>
                    <Fa>
                        <KodWaluty>PLN</KodWaluty>
                        <P_1>2026-01-15</P_1>
                        <P_2>KOR/1</P_2>
                        <P_6>2026-01-15</P_6>
                        <P_13_1>-100.00</P_13_1>
                        <P_14_1>-23.00</P_14_1>
                        <P_15>-123.00</P_15>
                        <RodzajFaktury>KOR</RodzajFaktury>
                        <FaWiersz>
                            <P_7>Correction</P_7>
                            <P_8B>1</P_8B>
                            <P_9A>-100.00</P_9A>
                            <P_11>-100.00</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <Platnosc>
                            <FormaPlatnosci>6</FormaPlatnosci>
                            <RachunekBankowy>12105000997603123456789123</RachunekBankowy>
                        </Platnosc>
                    </Fa>
                </Faktura>
                """);
        StoredFile storedFile = new StoredFile();
        storedFile.setStoragePath(xmlFile.getAbsolutePath());
        Invoice invoice = new Invoice();
        Organization organization = new Organization();
        ReflectionTestUtils.setField(organization, "id", organizationId);
        invoice.setOrganization(organization);
        invoice.setFile(storedFile);
        invoice.setStatus(InvoiceStatus.INVALID);

        ValidationResult validationResult = new ValidationResult();
        validationResult.setInvoice(invoice);
        validationResult.setStatus(ValidationStatus.INVALID);
        ValidationMessage oldMessage = new ValidationMessage();
        oldMessage.setSeverity(ValidationSeverity.ERROR);
        oldMessage.setCode("XML_SCHEMA_INVALID");
        oldMessage.setMessage("Old schema error");
        validationResult.addMessage(oldMessage);

        when(invoiceRepository.findByIdAndOrganizationId(invoiceId, organizationId)).thenReturn(Optional.of(invoice));
        when(validationResultRepository.findByInvoiceIdAndInvoiceOrganizationId(invoiceId, organizationId))
                .thenReturn(Optional.of(validationResult));
        when(invoiceRepository.save(invoice)).thenReturn(invoice);
        when(validationResultRepository.save(validationResult)).thenReturn(validationResult);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        when(fileStorageService.withLocalFile(eq(storedFile), any())).thenAnswer(invocation -> {
            Function<File, ?> operation = invocation.getArgument(1);
            return operation.apply(xmlFile);
        });

        InvoiceService service = new InvoiceService(
                invoiceRepository,
                validationResultRepository,
                null,
                fileStorageService,
                new FixedCurrentUserService(organizationId),
                null,
                new XmlTechnicalValidationService(fileToValidate -> validSchemaResult()),
                new InvoiceXmlParser(),
                new BusinessValidationService(),
                new InvoiceMapper(),
                mock(OrganizationAuthorizationService.class),
                mock(RateLimitService.class),
                mock(AuditEventService.class)
        );

        InvoiceValidationResponse response = service.revalidate(invoiceId);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VALID);
        assertThat(invoice.getGrossAmount()).isEqualByComparingTo(new BigDecimal("-123.00"));
        assertThat(invoice.getItems()).hasSize(1);
        assertThat(validationResult.getMessages()).isEmpty();
        assertThat(response.status()).isEqualTo(ValidationStatus.VALID);
        verify(invoiceRepository).save(invoice);
        verify(validationResultRepository).save(validationResult);
    }

    private static final class FixedCurrentUserService extends CurrentUserService {
        private final UUID organizationId;

        private FixedCurrentUserService(UUID organizationId) {
            super(null, null);
            this.organizationId = organizationId;
        }

        @Override
        public UUID currentOrganizationId() {
            return organizationId;
        }
    }

    private static com.ksefhelper.validation.SchemaValidationResult validSchemaResult() {
        return com.ksefhelper.validation.SchemaValidationResult.validResult();
    }
}
