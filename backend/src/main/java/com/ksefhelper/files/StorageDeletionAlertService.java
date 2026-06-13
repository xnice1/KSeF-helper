package com.ksefhelper.files;

import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StorageDeletionAlertService {
    private static final Logger log = LoggerFactory.getLogger(StorageDeletionAlertService.class);

    private final StorageDeletionTaskRepository repository;

    public StorageDeletionAlertService(StorageDeletionTaskRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${app.storage.cleanup.alert-interval:15m}")
    public void reportDeadLetters() {
        long failed = repository.countByFailedAtIsNotNull();
        if (failed > 0) {
            log.error(
                    "ACTION_REQUIRED storage deletion dead-letter queue contains {} task(s); "
                            + "review /api/admin/storage-deletions/failed",
                    failed
            );
        }
    }
}
