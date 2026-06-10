package com.ksefhelper.validation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class OfficialFa3Samples {
    private static final Path ROOT = Path.of("src/test/resources/sample-invoices/fa3-official");

    private OfficialFa3Samples() {
    }

    static Stream<Path> all() {
        return files().stream();
    }

    static List<Path> files() {
        try (Stream<Path> paths = Files.walk(ROOT)) {
            List<Path> files = paths
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".xml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (files.size() != 26) {
                throw new IllegalStateException("Expected 26 official FA(3) samples, but found " + files.size() + ".");
            }
            return files;
        } catch (IOException ex) {
            throw new UncheckedIOException("Official FA(3) samples could not be listed.", ex);
        }
    }

    static Path named(String filename) {
        return files().stream()
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(filename))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Official FA(3) sample was not found: " + filename));
    }
}
