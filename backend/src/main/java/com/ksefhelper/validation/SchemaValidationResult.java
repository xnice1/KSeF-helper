package com.ksefhelper.validation;

public record SchemaValidationResult(
        boolean valid,
        Integer line,
        Integer column,
        String message
) {
    public static SchemaValidationResult validResult() {
        return new SchemaValidationResult(true, null, null, null);
    }

    public static SchemaValidationResult invalid(int line, int column, String message) {
        return new SchemaValidationResult(false, line, column, message);
    }

    public String location() {
        if (line == null || line < 1) {
            return null;
        }
        return column == null || column < 1
                ? "line " + line
                : "line " + line + ", column " + column;
    }
}
