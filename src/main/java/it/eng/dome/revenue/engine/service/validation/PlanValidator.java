package it.eng.dome.revenue.engine.service.validation;

import java.util.ArrayList;
import java.util.List;

import it.eng.dome.revenue.engine.model.Plan;

public class PlanValidator {

    public PlanValidationReport validate(Plan plan) {

        PlanValidationReport report = new PlanValidationReport();

        report.addIssues(this.validatePlanMetadata(plan));

        return report;
    }

    private List<PlanValidationIssue> validatePlanMetadata(Plan plan) {
        List<PlanValidationIssue> issues = new ArrayList<>();

        // make sure there's a name
        if(plan.getName()==null || plan.getName().isEmpty())
            issues.add(new PlanValidationIssue("the plan must have a name", PlanValidationIssueSeverity.ERROR));
        // make sure there's a description
        if(plan.getDescription()==null || plan.getDescription().isEmpty())
            issues.add(new PlanValidationIssue( "the plan must include a description", PlanValidationIssueSeverity.ERROR));
        // make sure there's a lifecycle status
        if(plan.getLifecycleStatus()==null || plan.getLifecycleStatus().isEmpty())
            issues.add(new PlanValidationIssue( "the plan must include a lifecycle status", PlanValidationIssueSeverity.ERROR));

        // make sure there is billing cycle information
        if(plan.getBillCycleSpecification().getBillingPeriodType()==null)
            issues.add(new PlanValidationIssue( "the plan must include a billingPeriodLength (an integer)", PlanValidationIssueSeverity.ERROR));
        if(plan.getBillCycleSpecification().getBillingPeriodType()==null)
            issues.add(new PlanValidationIssue( "the plan must include a billingPeriodType (i.e. YEAR, MONTH, WEEK, DAY)", PlanValidationIssueSeverity.ERROR));
        if(plan.getBillCycleSpecification().getBillingPeriodEnd()==null)
            issues.add(new PlanValidationIssue( "the plan does not provide a 'billingPeriodEnd'. Assuming 'COMPUTED_DAY'", PlanValidationIssueSeverity.WARNING));

        return issues;
    }

}
