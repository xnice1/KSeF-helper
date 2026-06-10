package com.ksefhelper.validation;

import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PythonFa3ValidationRunner implements Fa3ValidationRunner {
    private static final List<String> SCHEMA_FILES = List.of(
            "schemat_fa_vat-3-_v1-0.xsd",
            "StrukturyDanych_v10-0E.xsd",
            "ElementarneTypyDanych_v10-0E.xsd",
            "KodyKrajow_v10-0E.xsd"
    );

    private final String validatorCommand;
    private final Duration timeout;
    private final Path scriptPath;
    private final Path schemaPath;

    public PythonFa3ValidationRunner(
            Resource xsdResource,
            Resource validatorScript,
            String validatorCommand,
            Duration timeout
    ) throws IOException {
        this.validatorCommand = validatorCommand;
        this.timeout = timeout;
        Path workDirectory = Files.createTempDirectory("ksef-fa3-validator-");
        workDirectory.toFile().deleteOnExit();
        this.scriptPath = copy(validatorScript, workDirectory.resolve("validate_fa3.py"));
        for (String filename : SCHEMA_FILES) {
            Resource resource = filename.equals(xsdResource.getFilename())
                    ? xsdResource
                    : xsdResource.createRelative(filename);
            copy(resource, workDirectory.resolve(filename));
        }
        this.schemaPath = workDirectory.resolve(xsdResource.getFilename());
    }

    @Override
    public SchemaValidationResult validate(File xmlFile) throws Exception {
        Process process = new ProcessBuilder(
                validatorCommand,
                scriptPath.toString(),
                schemaPath.toString(),
                xmlFile.getAbsolutePath()
        )
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("FA(3) schema validation timed out.");
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() == 0) {
            return SchemaValidationResult.validResult();
        }
        if (process.exitValue() == 2) {
            String[] fields = output.split("\\t", 3);
            int line = integer(fields, 0);
            int column = integer(fields, 1);
            String message = fields.length > 2 && !fields[2].isBlank()
                    ? fields[2]
                    : "schema validation failed.";
            return SchemaValidationResult.invalid(line, column, message);
        }
        throw new IOException(output.isBlank() ? "FA(3) validator process failed." : output);
    }

    private Path copy(Resource resource, Path target) throws IOException {
        try (var inputStream = resource.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        target.toFile().deleteOnExit();
        return target;
    }

    private int integer(String[] fields, int index) {
        if (fields.length <= index) {
            return -1;
        }
        try {
            return Integer.parseInt(fields[index]);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
