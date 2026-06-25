package com.ksefhelper.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class ValidationConfiguration {
    @Bean
    ValidatorCapacityLimiter validatorCapacityLimiter(
            @Value("${app.xml.max-concurrent-validations:2}") int maximum,
            @Value("${app.xml.capacity-acquire-timeout:2s}") Duration acquireTimeout
    ) {
        return new ValidatorCapacityLimiter(maximum, acquireTimeout);
    }

    @Bean
    Fa3ValidationRunner fa3ValidationRunner(
            @Value("${app.xml.xsd-path}") Resource xsdResource,
            @Value("${app.xml.validator-script}") Resource validatorScript,
            @Value("${app.xml.validator-command}") String validatorCommand,
            @Value("${app.xml.validation-timeout}") Duration validationTimeout,
            ValidatorCapacityLimiter capacityLimiter,
            @Value("${app.xml.memory-limit-mb:256}") int memoryLimitMb,
            @Value("${app.xml.cpu-limit-seconds:10}") int cpuLimitSeconds,
            @Value("${app.xml.max-output-bytes:16384}") int maxOutputBytes
    ) throws IOException {
        return new PythonFa3ValidationRunner(
                xsdResource,
                validatorScript,
                validatorCommand,
                validationTimeout,
                capacityLimiter,
                memoryLimitMb,
                cpuLimitSeconds,
                maxOutputBytes
        );
    }
}
