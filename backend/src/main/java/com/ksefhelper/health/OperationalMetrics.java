package com.ksefhelper.health;

import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import com.ksefhelper.validation.ValidatorCapacityLimiter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {
    public OperationalMetrics(
            MeterRegistry meterRegistry,
            StorageDeletionTaskRepository deletionTaskRepository,
            ValidatorCapacityLimiter validatorCapacityLimiter
    ) {
        Gauge.builder(
                        "ksef.storage.deletion.pending",
                        deletionTaskRepository,
                        StorageDeletionTaskRepository::countByCompletedAtIsNullAndFailedAtIsNull
                )
                .description("Storage deletion tasks waiting for processing")
                .register(meterRegistry);
        Gauge.builder(
                        "ksef.storage.deletion.failed",
                        deletionTaskRepository,
                        StorageDeletionTaskRepository::countByFailedAtIsNotNull
                )
                .description("Storage deletion tasks in the dead-letter state")
                .register(meterRegistry);
        Gauge.builder("ksef.validator.active", validatorCapacityLimiter, ValidatorCapacityLimiter::active)
                .description("Active FA(3) validator processes")
                .register(meterRegistry);
        Gauge.builder("ksef.validator.capacity", validatorCapacityLimiter, ValidatorCapacityLimiter::maximum)
                .description("Maximum concurrent FA(3) validator processes")
                .register(meterRegistry);
    }
}
