package com.ksefhelper.files;

import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.files.entity.StorageDeletionTask;
import com.ksefhelper.files.entity.StoredFile;
import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import com.ksefhelper.files.repository.StoredFileRepository;
import com.ksefhelper.files.storage.ObjectStorage;
import com.ksefhelper.organizations.entity.Organization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

@Service
public class FileStorageService {
    private final long maxUploadBytes;
    private final StoredFileRepository storedFileRepository;
    private final StorageDeletionTaskRepository deletionTaskRepository;
    private final StorageDeletionWorker deletionWorker;
    private final StorageCleanupFailureRecorder cleanupFailureRecorder;
    private final ObjectStorage objectStorage;

    public FileStorageService(
            @Value("${app.storage.max-upload-bytes}") long maxUploadBytes,
            StoredFileRepository storedFileRepository,
            StorageDeletionTaskRepository deletionTaskRepository,
            StorageDeletionWorker deletionWorker,
            StorageCleanupFailureRecorder cleanupFailureRecorder,
            ObjectStorage objectStorage
    ) {
        this.maxUploadBytes = maxUploadBytes;
        this.storedFileRepository = storedFileRepository;
        this.deletionTaskRepository = deletionTaskRepository;
        this.deletionWorker = deletionWorker;
        this.cleanupFailureRecorder = cleanupFailureRecorder;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public StoredFile storeXml(MultipartFile multipartFile, Organization organization) {
        validate(multipartFile);
        String originalFilename = StringUtils.cleanPath(
                multipartFile.getOriginalFilename() == null ? "invoice.xml" : multipartFile.getOriginalFilename()
        );
        try {
            byte[] bytes = multipartFile.getBytes();
            String checksum = sha256(bytes);
            String storageKey = organization.getId() + "/" + UUID.randomUUID() + ".xml";
            String contentType = multipartFile.getContentType() == null
                    ? "application/xml"
                    : multipartFile.getContentType();
            objectStorage.put(storageKey, bytes, contentType, checksum);
            deleteOnRollback(storageKey);

            StoredFile storedFile = new StoredFile();
            storedFile.setOrganization(organization);
            storedFile.setOriginalFilename(originalFilename);
            storedFile.setStoragePath(storageKey);
            storedFile.setContentType(contentType);
            storedFile.setSizeBytes(bytes.length);
            storedFile.setChecksum(checksum);
            return storedFileRepository.save(storedFile);
        } catch (IOException ex) {
            throw new BadRequestException("The XML file could not be read.");
        }
    }

    public Resource load(StoredFile storedFile) {
        byte[] bytes = verifiedBytes(storedFile);
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return storedFile.getOriginalFilename();
            }
        };
    }

    public <T> T withLocalFile(StoredFile storedFile, Function<File, T> operation) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("ksef-invoice-", ".xml");
            Files.write(temporary, verifiedBytes(storedFile));
            return operation.apply(temporary.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("A temporary validation file could not be created.", ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The operating system will eventually clear its temporary directory.
                }
            }
        }
    }

    @Transactional
    public void scheduleDeletion(StoredFile storedFile) {
        StorageDeletionTask task = new StorageDeletionTask();
        task.setStorageKey(storedFile.getStoragePath());
        task.setNextAttemptAt(Instant.now());
        StorageDeletionTask saved = deletionTaskRepository.save(task);
        afterCommit(() -> deletionWorker.process(saved.getId()));
    }

    public byte[] exportForBackup(StoredFile storedFile) {
        return verifiedBytes(storedFile);
    }

    public void restoreFromBackup(StoredFile storedFile, byte[] bytes) {
        if (!sha256(bytes).equals(storedFile.getChecksum())) {
            throw new BadRequestException("Backup checksum does not match the stored file record.");
        }
        objectStorage.put(
                storedFile.getStoragePath(),
                bytes,
                storedFile.getContentType(),
                storedFile.getChecksum()
        );
    }

    private byte[] verifiedBytes(StoredFile storedFile) {
        byte[] bytes = objectStorage.read(storedFile.getStoragePath());
        if (!sha256(bytes).equals(storedFile.getChecksum())) {
            throw new IllegalStateException("Stored XML checksum verification failed.");
        }
        return bytes;
    }

    private void validate(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BadRequestException("Upload an XML file that is not empty.");
        }
        if (multipartFile.getSize() > maxUploadBytes) {
            throw new BadRequestException("The XML file is too large.");
        }
        String filename = multipartFile.getOriginalFilename();
        if (filename != null && !filename.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new BadRequestException("Only .xml files are supported in this MVP.");
        }
    }

    private void deleteOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        objectStorage.delete(storageKey);
                    } catch (RuntimeException ex) {
                        cleanupFailureRecorder.record(storageKey, ex);
                    }
                }
            }
        });
    }

    private void afterCommit(Runnable operation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            operation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                operation.run();
            }
        });
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
