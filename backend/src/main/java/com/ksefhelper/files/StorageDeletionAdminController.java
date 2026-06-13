package com.ksefhelper.files;

import com.ksefhelper.files.dto.StorageDeletionTaskResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/storage-deletions")
public class StorageDeletionAdminController {
    private final StorageDeletionAdminService service;

    public StorageDeletionAdminController(StorageDeletionAdminService service) {
        this.service = service;
    }

    @GetMapping("/failed")
    public List<StorageDeletionTaskResponse> failedTasks() {
        return service.failedTasks();
    }

    @PostMapping("/{taskId}/requeue")
    public StorageDeletionTaskResponse requeue(@PathVariable UUID taskId) {
        return service.requeue(taskId);
    }
}
