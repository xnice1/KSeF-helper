package com.ksefhelper.validation;

public final class PythonTestSupport {
    private PythonTestSupport() {
    }

    public static String command() {
        String systemProperty = System.getProperty("xml.validator.command");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }
        String environmentVariable = System.getenv("XML_VALIDATOR_COMMAND");
        if (environmentVariable != null && !environmentVariable.isBlank()) {
            return environmentVariable;
        }
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "python" : "python3";
    }
}
