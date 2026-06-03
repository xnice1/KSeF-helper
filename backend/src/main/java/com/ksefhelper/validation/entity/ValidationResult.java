package com.ksefhelper.validation.entity;

import com.ksefhelper.common.entity.AuditableEntity;
import com.ksefhelper.invoices.entity.Invoice;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "validation_results")
public class ValidationResult extends AuditableEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, unique = true)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValidationStatus status;

    @OneToMany(mappedBy = "validationResult", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ValidationMessage> messages = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public void setStatus(ValidationStatus status) {
        this.status = status;
    }

    public List<ValidationMessage> getMessages() {
        return messages;
    }

    public void addMessage(ValidationMessage message) {
        message.setValidationResult(this);
        messages.add(message);
    }
}
