package com.ksefhelper.validation;

import com.ksefhelper.validation.entity.ValidationSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class XmlTechnicalValidationServiceTest {
    @Test
    void warnsWhenFa3XmlIsCheckedWithPlaceholderSchema() throws Exception {
        XmlTechnicalValidationService service = new XmlTechnicalValidationService(
                new FileSystemResource(Path.of("src/main/resources/xsd/ksef-placeholder.xsd"))
        );
        File file = File.createTempFile("fa3-placeholder-schema", ".xml");
        java.nio.file.Files.writeString(file.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Faktura xmlns="http://crd.gov.pl/wzor/2025/06/25/13775/">
                    <Fa>
                        <P_2>FV2026/02/150</P_2>
                    </Fa>
                </Faktura>
                """);

        var issues = service.validate(file);

        assertThat(issues)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.severity()).isEqualTo(ValidationSeverity.WARNING);
                    assertThat(issue.code()).isEqualTo("FA3_SCHEMA_VALIDATION_NOT_ENABLED");
                });
    }
}
