package com.ksefhelper.validation;

import com.ksefhelper.validation.dto.ParsedInvoice;
import com.ksefhelper.validation.dto.ParsedInvoiceItem;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class InvoiceXmlParser {
    public ParsedInvoice parse(File xmlFile) {
        try {
            DocumentBuilder builder = XmlSecurity.secureDocumentBuilderFactory().newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            XPath xpath = XPathFactory.newInstance().newXPath();

            return new ParsedInvoice(
                    text(xpath, document, "InvoiceNumber", "P_2", "NrFaktury"),
                    date(text(xpath, document, "IssueDate", "P_1", "DataWystawienia")),
                    date(text(xpath, document, "SaleDate", "P_6", "DataSprzedazy")),
                    firstPath(xpath, document,
                            "//*[local-name()='Seller']/*[local-name()='Name']",
                            "//*[local-name()='Podmiot1']//*[local-name()='Nazwa']",
                            "//*[local-name()='Sprzedawca']//*[local-name()='Nazwa']"),
                    firstPath(xpath, document,
                            "//*[local-name()='Seller']/*[local-name()='Nip']",
                            "//*[local-name()='Seller']/*[local-name()='NIP']",
                            "//*[local-name()='Podmiot1']//*[local-name()='NIP']",
                            "//*[local-name()='Sprzedawca']//*[local-name()='NIP']"),
                    firstPath(xpath, document,
                            "//*[local-name()='Buyer']/*[local-name()='Name']",
                            "//*[local-name()='Podmiot2']//*[local-name()='Nazwa']",
                            "//*[local-name()='Nabywca']//*[local-name()='Nazwa']"),
                    firstPath(xpath, document,
                            "//*[local-name()='Buyer']/*[local-name()='Nip']",
                            "//*[local-name()='Buyer']/*[local-name()='NIP']",
                            "//*[local-name()='Podmiot2']//*[local-name()='NIP']",
                            "//*[local-name()='Nabywca']//*[local-name()='NIP']"),
                    text(xpath, document, "Currency", "KodWaluty", "Waluta"),
                    totalAmount(xpath, document, "P_13_", "NetAmount", "Netto"),
                    totalAmount(xpath, document, "P_14_", "VatAmount", "VAT"),
                    amount(text(xpath, document, "GrossAmount", "P_15", "Brutto")),
                    paymentMethod(text(xpath, document, "PaymentMethod", "FormaPlatnosci")),
                    text(xpath, document, "BankAccount", "NrRB", "RachunekBankowy"),
                    text(xpath, document, "InvoiceType", "RodzajFaktury"),
                    items(xpath, document)
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("The XML file could not be parsed safely.", ex);
        }
    }

    private List<ParsedInvoiceItem> items(XPath xpath, Document document) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(
                "//*[local-name()='Item' or local-name()='FaWiersz']",
                document,
                XPathConstants.NODESET
        );
        List<ParsedInvoiceItem> items = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            items.add(new ParsedInvoiceItem(
                    firstPath(xpath, node,
                            ".//*[local-name()='Name']",
                            ".//*[local-name()='Description']",
                            ".//*[local-name()='P_7']",
                            ".//*[local-name()='Nazwa']"),
                    amount(firstPath(xpath, node, ".//*[local-name()='Quantity']", ".//*[local-name()='P_8B']", ".//*[local-name()='Ilosc']")),
                    amount(firstPath(xpath, node, ".//*[local-name()='UnitPrice']", ".//*[local-name()='P_9A']", ".//*[local-name()='CenaJednostkowa']")),
                    amount(firstPath(xpath, node, ".//*[local-name()='NetAmount']", ".//*[local-name()='P_11']", ".//*[local-name()='Netto']")),
                    firstPath(xpath, node, ".//*[local-name()='VatRate']", ".//*[local-name()='P_12']", ".//*[local-name()='StawkaVAT']"),
                    amount(firstPath(xpath, node, ".//*[local-name()='VatAmount']", ".//*[local-name()='KwotaVAT']", ".//*[local-name()='VAT']")),
                    amount(firstPath(xpath, node, ".//*[local-name()='GrossAmount']", ".//*[local-name()='Brutto']"))
            ));
        }
        return items;
    }

    private String text(XPath xpath, Document document, String... names) throws Exception {
        String[] paths = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            paths[i] = "//*[local-name()='" + names[i] + "']";
        }
        return firstPath(xpath, document, paths);
    }

    private String firstPath(XPath xpath, Object node, String... paths) throws Exception {
        for (String path : paths) {
            String value = (String) xpath.evaluate("normalize-space((" + path + ")[1])", node, XPathConstants.STRING);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private BigDecimal totalAmount(XPath xpath, Document document, String faPrefix, String... fallbackNames) throws Exception {
        BigDecimal fallback = amount(text(xpath, document, fallbackNames));
        BigDecimal faTotal = sumDirectFaAmounts(xpath, document, faPrefix);
        return faTotal == null ? fallback : faTotal;
    }

    private BigDecimal sumDirectFaAmounts(XPath xpath, Document document, String prefix) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(
                "//*[local-name()='Fa']/*[starts-with(local-name(), '" + prefix + "')]",
                document,
                XPathConstants.NODESET
        );
        BigDecimal total = BigDecimal.ZERO;
        boolean found = false;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String name = localName(node);
            if (name.endsWith("W")) {
                continue;
            }
            BigDecimal amount = amount(node.getTextContent());
            if (amount != null) {
                total = total.add(amount);
                found = true;
            }
        }
        return found ? total : null;
    }

    private String localName(Node node) {
        if (node.getLocalName() != null) {
            return node.getLocalName();
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private String paymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return switch (normalized) {
            case "1" -> "CASH";
            case "6" -> "TRANSFER";
            default -> normalized.toUpperCase(Locale.ROOT);
        };
    }

    private LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private BigDecimal amount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
