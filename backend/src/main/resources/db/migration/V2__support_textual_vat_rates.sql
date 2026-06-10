-- noinspection SqlDialectInspectionForFile
ALTER TABLE invoice_items
    ALTER COLUMN vat_rate TYPE VARCHAR(20)
    USING CASE
        WHEN vat_rate IS NULL THEN NULL
        ELSE TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM vat_rate::text))
    END;
