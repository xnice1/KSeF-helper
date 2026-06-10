package com.ksefhelper.validation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PythonFa3ValidationRunnerTest {
    private PythonFa3ValidationRunner runner;

    @BeforeAll
    void createRunner() throws Exception {
        runner = new PythonFa3ValidationRunner(
                new FileSystemResource(Path.of("src/main/resources/xsd/schemat_fa_vat-3-_v1-0.xsd")),
                new FileSystemResource(Path.of("src/main/resources/xml-validator/validate_fa3.py")),
                PythonTestSupport.command(),
                Duration.ofSeconds(10)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("officialSamples")
    void validatesEveryOfficialFa3Sample(Path sample) throws Exception {
        assertThat(runner.validate(sample.toFile()).valid()).isTrue();
    }

    @Test
    void rejectsXmlOutsideTheOfficialFa3Schema() throws Exception {
        File invalidFile = Path.of("src/test/resources/sample-invoices/sample-valid.xml").toFile();

        SchemaValidationResult invalidResult = runner.validate(invalidFile);

        assertThat(invalidResult.valid()).isFalse();
        assertThat(invalidResult.message()).contains("No matching global declaration");
    }

    Stream<Path> officialSamples() {
        return OfficialFa3Samples.all();
    }
}
