package it.eng.dome.revenue.engine.service.validation;

import java.util.ArrayList;
import java.util.List;

public class PlanValidationReport {

    private List<PlanValidationIssue> issues;

    public PlanValidationReport() {
        this.issues = new ArrayList<>();
    }

    public void addIssue(PlanValidationIssue issue) {
        this.issues.add(issue);
    }

    public void addIssues(List<PlanValidationIssue> issues) {
        this.issues.addAll(issues);
    }

    public List<PlanValidationIssue> getIssues() {
        // TODO: return issues sorted by severity
        return issues;
    }


}
