package com.ksefhelper.validation;

import com.ksefhelper.validation.dto.ValidationIssue;
import com.ksefhelper.validation.entity.ValidationSeverity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class XmlTechnicalValidationService {
    private final Schema schema;

    public XmlTechnicalValidationService(@Value("${app.xml.xsd-path}") Resource xsdResource) throws SAXException, IOException {
        this.schema = XmlSecurity.secureSchemaFactory().newSchema(xsdResource.getURL());
    }

    public List<ValidationIssue> validate(File xmlFile) {
        List<ValidationIssue> issues = new ArrayList<>();
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

    private String cleanXmlMessage(String message) {
        if (message == null || message.isBlank()) {
            return "schema validation failed.";
        }
        return message.replaceAll("\\s+", " ").trim();
    }
}
