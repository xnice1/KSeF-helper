package com.ksefhelper.files.repository;

import com.ksefhelper.files.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    Optional<StoredFile> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<StoredFile> findAllByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

    @Query("select coalesce(sum(file.sizeBytes), 0) from StoredFile file where file.organization.id = :organizationId")
    long totalSizeByOrganizationId(@Param("organizationId") UUID organizationId);
}
