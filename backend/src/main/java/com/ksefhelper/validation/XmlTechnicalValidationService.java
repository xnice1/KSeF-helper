package com.ksefhelper.validation;

import com.ksefhelper.validation.dto.ValidationIssue;
import com.ksefhelper.validation.entity.ValidationSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class XmlTechnicalValidationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(XmlTechnicalValidationService.class);

    private final Fa3ValidationRunner validationRunner;

    @Autowired
    public XmlTechnicalValidationService(
            @Value("${app.xml.xsd-path}") Resource xsdResource,
            @Value("${app.xml.validator-script}") Resource validatorScript,
            @Value("${app.xml.validator-command}") String validatorCommand,
            @Value("${app.xml.validation-timeout}") Duration validationTimeout
    ) throws IOException {
        this(new PythonFa3ValidationRunner(
                xsdResource,
                validatorScript,
                validatorCommand,
                validationTimeout
        ));
    }

    public XmlTechnicalValidationService(Fa3ValidationRunner validationRunner) {
        this.validationRunner = validationRunner;
    }

    public List<ValidationIssue> validate(File xmlFile) {
        List<ValidationIssue> issues = new ArrayList<>();
        try {
            secureDocument(xmlFile);
        } catch (SAXParseException ex) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "XML_PARSE_FAILED",
                    "line " + ex.getLineNumber() + ", column " + ex.getColumnNumber(),
                    "The XML file is not well-formed or contains a forbidden declaration.",
                    "Check the XML syntax and remove DOCTYPE or external entity declarations."
            ));
            return issues;
        } catch (Exception ex) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "XML_PARSE_FAILED",
                    null,
                    "The XML file could not be read safely.",
                    "Check that the file is well-formed XML and does not contain unsupported external resources."
            ));
            return issues;
        }

        try {
            SchemaValidationResult result = validationRunner.validate(xmlFile);
            if (!result.valid()) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.ERROR,
                        "XML_SCHEMA_INVALID",
                        result.location(),
                        "XML does not match the official FA(3) invoice schema: " + result.message(),
                        "Check the highlighted XML field against the official FA(3) schema."
                ));
            }
        } catch (Exception ex) {
            LOGGER.error("Official FA(3) schema validation failed.", ex);
            issues.add(new ValidationIssue(
                    ValidationSeverity.ERROR,
                    "XML_SCHEMA_VALIDATION_FAILED",
                    null,
                    "Official FA(3) schema validation is temporarily unavailable.",
                    "Try again or contact the system administrator."
            ));
        }
        return issues;
    }

    private Document secureDocument(File xmlFile) throws Exception {
        DocumentBuilder builder = XmlSecurity.secureDocumentBuilderFactory().newDocumentBuilder();
        return builder.parse(xmlFile);
    }
}
