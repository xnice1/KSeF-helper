package com.ksefhelper.organizations;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.files.FileStorageService;
import com.ksefhelper.files.entity.StoredFile;
import com.ksefhelper.files.repository.StoredFileRepository;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.organizations.repository.OrganizationRepository;
import com.ksefhelper.users.entity.User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DataDeletionService {
    private final StoredFileRepository storedFileRepository;
    private final FileStorageService fileStorageService;
    private final OrganizationRepository organizationRepository;
    private final AuditEventService auditEventService;

    public DataDeletionService(
            StoredFileRepository storedFileRepository,
            FileStorageService fileStorageService,
            OrganizationRepository organizationRepository,
            AuditEventService auditEventService
    ) {
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.organizationRepository = organizationRepository;
        this.auditEventService = auditEventService;
    }

    public void deleteOrganization(Organization organization, User actor, String reason) {
        var files = storedFileRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organization.getId());
        for (StoredFile file : files) {
            fileStorageService.scheduleDeletion(file);
        }
        auditEventService.recordForUser(
                AuditEventType.ORGANIZATION_DELETED,
                actor,
                organization.getId(),
                "organization",
                organization.getId(),
                Map.of(
                        "name", organization.getName(),
                        "reason", reason,
                        "fileCount", files.size()
                )
        );
        organizationRepository.deleteTenantDataById(organization.getId());
    }
}
