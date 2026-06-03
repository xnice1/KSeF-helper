package com.ksefhelper.files;

import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.files.entity.StoredFile;
import com.ksefhelper.files.repository.StoredFileRepository;
import com.ksefhelper.organizations.entity.Organization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path rootPath;
    private final long maxUploadBytes;
    private final StoredFileRepository storedFileRepository;

    public FileStorageService(
            @Value("${app.storage.local-path}") String localPath,
            @Value("${app.storage.max-upload-bytes}") long maxUploadBytes,
            StoredFileRepository storedFileRepository
    ) {
        this.rootPath = Path.of(localPath).toAbsolutePath().normalize();
        this.maxUploadBytes = maxUploadBytes;
        this.storedFileRepository = storedFileRepository;
    }

    @Transactional
    public StoredFile storeXml(MultipartFile multipartFile, Organization organization) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BadRequestException("Upload an XML file that is not empty.");
        }
        if (multipartFile.getSize() > maxUploadBytes) {
            throw new BadRequestException("The XML file is too large.");
        }

        String originalFilename = StringUtils.cleanPath(
                multipartFile.getOriginalFilename() == null ? "invoice.xml" : multipartFile.getOriginalFilename()
        );
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new BadRequestException("Only .xml files are supported in this MVP.");
        }

        try {
            byte[] bytes = multipartFile.getBytes();
            String checksum = sha256(bytes);
            String generatedName = UUID.randomUUID() + ".xml";
            Path organizationPath = rootPath.resolve(organization.getId().toString()).normalize();
            Path targetPath = organizationPath.resolve(generatedName).normalize();
            if (!targetPath.startsWith(rootPath)) {
                throw new BadRequestException("Invalid storage path.");
            }
            Files.createDirectories(organizationPath);
            Files.write(targetPath, bytes);

            StoredFile storedFile = new StoredFile();
            storedFile.setOrganization(organization);
            storedFile.setOriginalFilename(originalFilename);
            storedFile.setStoragePath(targetPath.toString());
            storedFile.setContentType(multipartFile.getContentType() == null ? "application/xml" : multipartFile.getContentType());
            storedFile.setSizeBytes(bytes.length);
            storedFile.setChecksum(checksum);
            return storedFileRepository.save(storedFile);
        } catch (IOException ex) {
            throw new BadRequestException("The XML file could not be stored.");
        }
    }

    public Resource load(StoredFile storedFile) {
        return new FileSystemResource(Path.of(storedFile.getStoragePath()));
    }

    public void deletePhysicalFile(StoredFile storedFile) {
        try {
            Files.deleteIfExists(Path.of(storedFile.getStoragePath()));
        } catch (IOException ignored) {
        }
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
