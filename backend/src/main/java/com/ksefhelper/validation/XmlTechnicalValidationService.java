package com.ksefhelper.validation;

import com.ksefhelper.validation.dto.ValidationIssue;
import com.ksefhelper.validation.entity.ValidationSeverity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class XmlTechnicalValidationService {
    private final Schema schema;
    private final String xsdFilename;

    public XmlTechnicalValidationService(@Value("${app.xml.xsd-path}") Resource xsdResource) throws SAXException, IOException {
        this.schema = XmlSecurity.secureSchemaFactory().newSchema(xsdResource.getURL());
        this.xsdFilename = xsdResource.getFilename();
    }

    public List<ValidationIssue> validate(File xmlFile) {
        List<ValidationIssue> issues = new ArrayList<>();
        Optional<String> rootName = rootName(xmlFile);
        if (usesPlaceholderSchema() && rootName.filter("Faktura"::equals).isPresent()) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "FA3_SCHEMA_VALIDATION_NOT_ENABLED",
                    "Faktura",
                    "Official FA(3) invoice XML was detected, but full FA(3) schema validation is not enabled in this runtime.",
                    "The app will parse and run business checks, but production FA(3) schema validation still needs a dedicated validation strategy."
            ));
            return issues;
        }
        try {
            Validator validator = schema.newValidator();
            validator.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(xmlFile));
        } catch (SAXParseException ex) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "XML_SCHEMA_INVALID",
                    "line " + ex.getLineNumber() + ", column " + ex.getColumnNumber(),
                    "XML does not match the invoice schema: " + cleanXmlMessage(ex.getMessage()),
                    "Check the highlighted XML field against the configured FA(3) schema."
            ));
        } catch (Exception ex) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "XML_SCHEMA_VALIDATION_FAILED",
                    null,
                    "The XML file could not be validated against the schema.",
                    "Check that the uploaded file is well-formed XML and that the configured XSD is available."
            ));
        }
        return issues;
    }

    private boolean usesPlaceholderSchema() {
        return "ksef-placeholder.xsd".equals(xsdFilename);
    }

    private Optional<String> rootName(File xmlFile) {
        try {
            DocumentBuilder builder = XmlSecurity.secureDocumentBuilderFactory().newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            String localName = document.getDocumentElement().getLocalName();
            return Optional.ofNullable(localName == null ? document.getDocumentElement().getNodeName() : localName);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String cleanXmlMessage(String message) {
        if (message == null || message.isBlank()) {
            return "schema validation failed.";
        }
        return message.replaceAll("\\s+", " ").trim();
    }
}
