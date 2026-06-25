package com.ksefhelper.validation;

import java.io.File;

@FunctionalInterface
public interface Fa3ValidationRunner {
    SchemaValidationResult validate(File xmlFile) throws Exception;

    default boolean healthCheck() throws Exception {
        return true;
    }
}
