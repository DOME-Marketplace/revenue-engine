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
        issues.sort((i1, i2) -> i1.getSeverity().ordinal() - i2.getSeverity().ordinal());
        return issues;
    }


}
