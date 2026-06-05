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
    void parsesOfficialFa3SampleInvoice() throws Exception {
        File file = fa3InvoiceFile();

        ParsedInvoice invoice = parser.parse(file);

        assertThat(invoice.invoiceNumber()).isEqualTo("FV2026/02/150");
        assertThat(invoice.sellerNip()).isEqualTo("9999999999");
        assertThat(invoice.buyerNip()).isEqualTo("1111111111");
        assertThat(invoice.currency()).isEqualTo("PLN");
        assertThat(invoice.netAmount()).isEqualByComparingTo(new BigDecimal("1667.61"));
        assertThat(invoice.vatAmount()).isEqualByComparingTo(new BigDecimal("383.38"));
        assertThat(invoice.grossAmount()).isEqualByComparingTo(new BigDecimal("2051"));
        assertThat(invoice.paymentMethod()).isEqualTo("TRANSFER");
        assertThat(invoice.items()).hasSize(3);
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

    private File fa3InvoiceFile() throws Exception {
        File file = File.createTempFile("fa3-sample", ".xml");
        java.nio.file.Files.writeString(file.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Faktura xmlns="http://crd.gov.pl/wzor/2025/06/25/13775/">
                    <Podmiot1>
                        <DaneIdentyfikacyjne>
                            <NIP>9999999999</NIP>
                            <Nazwa>ABC AGD sp. z o.o.</Nazwa>
                        </DaneIdentyfikacyjne>
                    </Podmiot1>
                    <Podmiot2>
                        <DaneIdentyfikacyjne>
                            <NIP>1111111111</NIP>
                            <Nazwa>F.H.U. Jan Kowalski</Nazwa>
                        </DaneIdentyfikacyjne>
                    </Podmiot2>
                    <Fa>
                        <KodWaluty>PLN</KodWaluty>
                        <P_1>2026-02-15</P_1>
                        <P_2>FV2026/02/150</P_2>
                        <P_6>2026-01-27</P_6>
                        <P_13_1>1666.66</P_13_1>
                        <P_14_1>383.33</P_14_1>
                        <P_13_3>0.95</P_13_3>
                        <P_14_3>0.05</P_14_3>
                        <P_15>2051</P_15>
                        <FaWiersz>
                            <P_7>lodowka Zimnotech mk1</P_7>
                            <P_8B>1</P_8B>
                            <P_9A>1626.01</P_9A>
                            <P_11>1626.01</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <FaWiersz>
                            <P_7>wniesienie sprzetu</P_7>
                            <P_8B>1</P_8B>
                            <P_9A>40.65</P_9A>
                            <P_11>40.65</P_11>
                            <P_12>23</P_12>
                        </FaWiersz>
                        <FaWiersz>
                            <P_7>promocja lodowka pelna mleka</P_7>
                            <P_8B>1</P_8B>
                            <P_9A>0.95</P_9A>
                            <P_11>0.95</P_11>
                            <P_12>5</P_12>
                        </FaWiersz>
                        <Platnosc>
                            <FormaPlatnosci>6</FormaPlatnosci>
                        </Platnosc>
                    </Fa>
                </Faktura>
                """);
        return file;
    }
}
