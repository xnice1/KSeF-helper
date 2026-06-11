package com.ksefhelper.files.storage;

import com.ksefhelper.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {
    private final Path rootPath;

    public LocalObjectStorage(@Value("${app.storage.local-path}") String localPath) {
        this.rootPath = Path.of(localPath).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType, String checksum) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("The file could not be stored.", ex);
        }
    }

    @Override
    public byte[] read(String key) {
        Path path = resolve(key);
        try {
            if (!Files.isRegularFile(path)) {
                throw new NotFoundException("Stored XML file was not found.");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalStateException("The stored XML file could not be read.", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new IllegalStateException("The stored XML file could not be deleted.", ex);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    private Path resolve(String key) {
        Path supplied = Path.of(key);
        if (supplied.isAbsolute()) {
            return supplied.normalize();
        }
        Path resolved = rootPath.resolve(key).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }
        return resolved;
    }
}
