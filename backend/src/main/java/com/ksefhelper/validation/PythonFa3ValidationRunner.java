package com.ksefhelper.validation;

import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
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
    private final ValidatorCapacityLimiter capacityLimiter;
    private final int memoryLimitMb;
    private final int cpuLimitSeconds;
    private final int maxOutputBytes;

    public PythonFa3ValidationRunner(
            Resource xsdResource,
            Resource validatorScript,
            String validatorCommand,
            Duration timeout,
            ValidatorCapacityLimiter capacityLimiter,
            int memoryLimitMb,
            int cpuLimitSeconds,
            int maxOutputBytes
    ) throws IOException {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Validator timeout must be positive.");
        }
        if (memoryLimitMb < 64 || cpuLimitSeconds <= 0 || maxOutputBytes <= 0) {
            throw new IllegalArgumentException("Validator resource limits must be positive and memory must be at least 64 MB.");
        }
        this.validatorCommand = validatorCommand;
        this.timeout = timeout;
        this.capacityLimiter = capacityLimiter;
        this.memoryLimitMb = memoryLimitMb;
        this.cpuLimitSeconds = cpuLimitSeconds;
        this.maxOutputBytes = maxOutputBytes;
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
        try (ValidatorCapacityLimiter.Lease ignored = capacityLimiter.acquire()) {
            Process process = new ProcessBuilder(
                    validatorCommand,
                    scriptPath.toString(),
                    schemaPath.toString(),
                    xmlFile.getAbsolutePath(),
                    Integer.toString(memoryLimitMb),
                    Integer.toString(cpuLimitSeconds)
            )
                    .redirectErrorStream(true)
                    .start();
            OutputCollector outputCollector = new OutputCollector(process.getInputStream(), maxOutputBytes);
            Thread outputThread = Thread.ofVirtual().start(outputCollector);

            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IOException("FA(3) schema validation timed out.");
            }
            outputThread.join(Duration.ofSeconds(5));
            String output = outputCollector.output().trim();
            if (outputCollector.truncated()) {
                throw new IOException("FA(3) validator output exceeded the configured limit.");
            }
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

    private static final class OutputCollector implements Runnable {
        private final InputStream input;
        private final int maximum;
        private byte[] output = new byte[0];
        private boolean truncated;
        private IOException failure;

        private OutputCollector(InputStream input, int maximum) {
            this.input = input;
            this.maximum = maximum;
        }

        @Override
        public void run() {
            try (input) {
                ByteArrayOutputStream captured = new ByteArrayOutputStream(Math.min(maximum, 4096));
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    int remaining = maximum - captured.size();
                    if (remaining > 0) {
                        captured.write(buffer, 0, Math.min(read, remaining));
                    }
                    if (read > remaining) {
                        truncated = true;
                    }
                }
                output = captured.toByteArray();
            } catch (IOException ex) {
                failure = ex;
            }
        }

        private String output() throws IOException {
            if (failure != null) {
                throw failure;
            }
            return new String(output, StandardCharsets.UTF_8);
        }

        private boolean truncated() {
            return truncated;
        }
    }
}
