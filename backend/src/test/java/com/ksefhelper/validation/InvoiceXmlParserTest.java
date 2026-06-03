package com.ksefhelper.validation;

import com.ksefhelper.validation.dto.ParsedInvoice;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceXmlParserTest {
    private final InvoiceXmlParser parser = new InvoiceXmlParser();

    @Test
    void parsesSampleInvoice() {
        File file = Path.of("src/test/resources/sample-invoices/sample-valid.xml").toFile();

        ParsedInvoice invoice = parser.parse(file);

        assertThat(invoice.invoiceNumber()).isEqualTo("FV/2026/001");
        assertThat(invoice.sellerNip()).isEqualTo("5250000000");
        assertThat(invoice.buyerNip()).isEqualTo("5210000000");
        assertThat(invoice.grossAmount()).isEqualByComparingTo(new BigDecimal("123.00"));
        assertThat(invoice.items()).hasSize(1);
    }

    @Test
    void rejectsDoctypeToProtectAgainstXxe() throws Exception {
        File file = File.createTempFile("xxe", ".xml");
        java.nio.file.Files.writeString(file.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Invoice [
                    <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <Invoice>
                    <InvoiceNumber>&xxe;</InvoiceNumber>
                </Invoice>
                """);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("could not be parsed safely");
    }
}
