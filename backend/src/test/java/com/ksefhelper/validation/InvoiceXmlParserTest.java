package com.ksefhelper.validation;

import com.ksefhelper.validation.dto.ParsedInvoice;
import com.ksefhelper.validation.dto.ParsedInvoiceItem;
import com.ksefhelper.validation.entity.ValidationSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceXmlParserTest {
    private final InvoiceXmlParser parser = new InvoiceXmlParser();
    private final BusinessValidationService businessValidationService = new BusinessValidationService();

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
        assertThat(invoice.invoiceType()).isEqualTo("VAT");
        assertThat(invoice.items()).hasSize(3);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("officialSamples")
    void parsesAndBusinessChecksEveryOfficialFa3Sample(Path sample) {
        ParsedInvoice invoice = parser.parse(sample.toFile());

        assertThat(invoice.invoiceNumber()).isNotBlank();
        assertThat(invoice.issueDate()).isNotNull();
        assertThat(invoice.sellerName()).isNotBlank();
        assertThat(invoice.currency()).isNotBlank();
        assertThat(invoice.invoiceType()).isNotBlank();
        assertThat(businessValidationService.validate(invoice))
                .noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    @Test
    void handlesCorrectionAdvanceSettlementForeignCurrencyAndExemptSamples() {
        ParsedInvoice correction = parseOfficial("FA_3_Przykład_2.xml");
        ParsedInvoice advance = parseOfficial("FA_3_Przykład_10.xml");
        ParsedInvoice settlement = parseOfficial("Fa_3_Przykład_14.xml");
        ParsedInvoice foreignCurrency = parseOfficial("Fa_3_Przykład_20.xml");
        ParsedInvoice exempt = parseOfficial("FA_3_Przykład_9.xml");

        assertThat(correction.invoiceType()).isEqualTo("KOR");
        assertThat(correction.grossAmount()).isEqualByComparingTo("-200");
        assertThat(businessValidationService.validate(correction))
                .noneMatch(issue -> issue.code().equals("GROSS_AMOUNT_INVALID"));

        assertThat(advance.invoiceType()).isEqualTo("ZAL");
        assertThat(advance.grossAmount()).isEqualByComparingTo("20000");

        assertThat(settlement.invoiceType()).isEqualTo("ROZ");
        assertThat(settlement.items()).hasSize(2);
        assertThat(settlement.grossAmount()).isEqualByComparingTo("307635");

        assertThat(foreignCurrency.currency()).isEqualTo("EUR");
        assertThat(foreignCurrency.vatAmount()).isEqualByComparingTo("3118.80");

        assertThat(exempt.items())
                .extracting(ParsedInvoiceItem::vatRate)
                .contains("zw");
    }

    @ParameterizedTest
    @ValueSource(strings = {"zw", "0 WDT", "0 EX", "oo", "np I", "np II"})
    void preservesTextualFa3VatRates(String vatRate) throws Exception {
        File file = File.createTempFile("fa3-vat-rate", ".xml");
        java.nio.file.Files.writeString(file.toPath(), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Faktura xmlns="http://crd.gov.pl/wzor/2025/06/25/13775/">
                    <Fa>
                        <P_2>FV/1</P_2>
                        <FaWiersz>
                            <P_7>Service</P_7>
                            <P_12>%s</P_12>
                        </FaWiersz>
                    </Fa>
                </Faktura>
                """.formatted(vatRate));

        ParsedInvoice invoice = parser.parse(file);

        assertThat(invoice.items()).singleElement()
                .extracting(ParsedInvoiceItem::vatRate)
                .isEqualTo(vatRate);
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
                        <RodzajFaktury>VAT</RodzajFaktury>
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

    private ParsedInvoice parseOfficial(String filename) {
        return parser.parse(OfficialFa3Samples.named(filename).toFile());
    }

    static Stream<Path> officialSamples() {
        return OfficialFa3Samples.all();
    }
}
