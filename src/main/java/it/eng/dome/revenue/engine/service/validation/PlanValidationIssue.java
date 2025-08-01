package it.eng.dome.revenue.engine.service.validation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlanValidationIssue {

    private String type;
    private String description;
    private String ref;
    private PlanValidationIssueSeverity severity;

    public PlanValidationIssue(String description, PlanValidationIssueSeverity severity) {
        this(null, description, null, severity);
    }

    public PlanValidationIssue(String type, String description, String reference, PlanValidationIssueSeverity severity) {
        this.type = type;
        this.description = description;
        this.ref = reference;
        this.severity = severity;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public PlanValidationIssueSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(PlanValidationIssueSeverity severity) {
        this.severity = severity;
    }

}
