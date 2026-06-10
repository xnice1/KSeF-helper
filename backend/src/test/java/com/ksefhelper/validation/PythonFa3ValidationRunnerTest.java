package com.ksefhelper.validation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PythonFa3ValidationRunnerTest {
    @Test
    void validatesThroughConfiguredPythonWorker() throws Exception {
        String command = System.getProperty("xml.validator.command");
        assumeTrue(command != null && !command.isBlank());

        PythonFa3ValidationRunner runner = new PythonFa3ValidationRunner(
                new FileSystemResource(Path.of("src/main/resources/xsd/schemat_fa_vat-3-_v1-0.xsd")),
                new FileSystemResource(Path.of("src/main/resources/xml-validator/validate_fa3.py")),
                command,
                Duration.ofSeconds(10)
        );
        File validFile = Path.of(
                "src/test/resources/sample-invoices/fa3-official",
                "Przykładowe pliki dla struktury logicznej e-Faktury FA(3)",
                "FA_3_Przykład_1.xml"
        ).toFile();
        File invalidFile = Path.of("src/test/resources/sample-invoices/sample-valid.xml").toFile();

        assertThat(runner.validate(validFile).valid()).isTrue();
        SchemaValidationResult invalidResult = runner.validate(invalidFile);
        assertThat(invalidResult.valid()).isFalse();
        assertThat(invalidResult.message()).contains("No matching global declaration");
    }
}
