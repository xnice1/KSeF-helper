package com.ksefhelper.validation;

import com.ksefhelper.validation.entity.ValidationSeverity;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class XmlTechnicalValidationServiceTest {
    @Test
    void acceptsXmlWhenOfficialSchemaRunnerAcceptsIt() {
        XmlTechnicalValidationService service = new XmlTechnicalValidationService(
                xmlFile -> SchemaValidationResult.validResult()
        );
        File file = officialSample();

        var issues = service.validate(file);

        assertThat(issues).isEmpty();
    }

    @Test
    void returnsOfficialSchemaErrorFromRunner() {
        XmlTechnicalValidationService service = new XmlTechnicalValidationService(
                xmlFile -> SchemaValidationResult.invalid(
                        12,
                        8,
                        "Element 'P_15' is missing."
                )
        );
        File file = officialSample();

        var issues = service.validate(file);

        assertThat(issues)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.severity()).isEqualTo(ValidationSeverity.ERROR);
                    assertThat(issue.code()).isEqualTo("XML_SCHEMA_INVALID");
                    assertThat(issue.fieldPath()).isEqualTo("line 12, column 8");
                    assertThat(issue.message()).contains("official FA(3)");
                });
    }

    @Test
    void rejectsDoctypeBeforeStartingSchemaRunner() throws Exception {
        XmlTechnicalValidationService service = new XmlTechnicalValidationService(
                xmlFile -> SchemaValidationResult.validResult()
        );
        File file = File.createTempFile("fa3-doctype", ".xml");
        java.nio.file.Files.writeString(file.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Faktura [
                    <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <Faktura xmlns="http://crd.gov.pl/wzor/2025/06/25/13775/">
                    <Fa><P_2>&xxe;</P_2></Fa>
                </Faktura>
                """);

        var issues = service.validate(file);

        assertThat(issues)
                .singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("XML_PARSE_FAILED"));
    }

    private File officialSample() {
        return Path.of(
                "src/test/resources/sample-invoices/fa3-official",
                "Przykładowe pliki dla struktury logicznej e-Faktury FA(3)",
                "FA_3_Przykład_1.xml"
        ).toFile();
    }
}
