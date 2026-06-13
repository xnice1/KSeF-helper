package com.ksefhelper.organizations;

import com.ksefhelper.audit.dto.AuditEventResponse;
import com.ksefhelper.organizations.dto.OrganizationDeletionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/api/organizations/current")
public class OrganizationDataController {
    private final OrganizationDataService organizationDataService;

    public OrganizationDataController(OrganizationDataService organizationDataService) {
        this.organizationDataService = organizationDataService;
    }

    @GetMapping("/audit-events")
    public List<AuditEventResponse> auditEvents() {
        return organizationDataService.auditEvents();
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export() throws java.io.IOException {
        OrganizationDataService.ExportedOrganizationData export = organizationDataService.export();
        StreamingResponseBody body = output -> {
            try {
                Files.copy(export.path(), output);
            } finally {
                Files.deleteIfExists(export.path());
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(Files.size(export.path()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(export.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(body);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody OrganizationDeletionRequest request) {
        organizationDataService.deleteCurrentOrganization(request.password(), request.confirmation());
        return ResponseEntity.noContent().build();
    }
}
