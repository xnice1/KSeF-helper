package com.ksefhelper.files.repository;

import com.ksefhelper.files.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<StoredFile> findAllByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
