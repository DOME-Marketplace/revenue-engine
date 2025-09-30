package it.eng.dome.revenue.engine.service.validation;

import java.util.ArrayList;
import java.util.List;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;

public class PlanValidator {

    public PlanValidationReport validate(Plan plan) {

        PlanValidationReport report = new PlanValidationReport();

        report.addIssues(this.validatePlanMetadata(plan));

        return report;
    }

    private List<PlanValidationIssue> validatePlanMetadata(Plan plan) {
        List<PlanValidationIssue> issues = new ArrayList<>();

        issues.addAll(this.validateCommonProperties(plan));

        issues.addAll(this.validateBillingCycleSection(plan));

        if(plan.getPrice()!=null) {
            issues.addAll(this.validatePrice(null));
        }

        return issues;
    }

    private List<PlanValidationIssue> validateCommonProperties(Plan plan) {
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
        return issues;
    }

    private List<PlanValidationIssue> validateBillingCycleSection(Plan plan) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if(plan.getBillCycleSpecification().getBillingPeriodType()==null)
            issues.add(new PlanValidationIssue( "the plan must include a billingPeriodLength (an integer)", PlanValidationIssueSeverity.ERROR));
        if(plan.getBillCycleSpecification().getBillingPeriodType()==null)
            issues.add(new PlanValidationIssue( "the plan must include a billingPeriodType (i.e. YEAR, MONTH, WEEK, DAY)", PlanValidationIssueSeverity.ERROR));
        if(plan.getBillCycleSpecification().getBillingPeriodEnd()==null)
            issues.add(new PlanValidationIssue( "the plan does not provide a 'billingPeriodEnd'. Assuming 'COMPUTED_DAY'", PlanValidationIssueSeverity.WARNING));
        // TODO: other checks...
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItem(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();

        // TODO: common

        // TODO: applicable properties

        // TODO: ignore properties

        // TODO: amount and currency

        // TODO: computation properties

        // TODO: charge properties

        // TODO: bundle properties

        return issues;
    }

    private List<PlanValidationIssue> validatePrice(Price price) {
        List<PlanValidationIssue> issues = new ArrayList<>();

        issues.addAll(this.validatePlanItem(price));

        if(price.getPrices()!=null) {
            for(Price p: price.getPrices()) {
                issues.addAll(this.validatePrice(p));
            }
        }

        if(price.getDiscount()!=null)
            issues.addAll(this.validateDiscount(price.getDiscount()));

        //... TODO other price-specific checks

        return issues;
    }

    private List<PlanValidationIssue> validateDiscount(Discount discount) {
        List<PlanValidationIssue> issues = new ArrayList<>();

        issues.addAll(this.validatePlanItem(discount));

        if(discount.getDiscounts()!=null) {
            for(Discount d: discount.getDiscounts()) {
                issues.addAll(this.validateDiscount(d));
            }
        }

        //... TODO other discount-specific checks

        return issues;
    }

    // ERROR bundle is forEach => forEachMetric expected
    // WARNING forEachMetric found & bundle!=forEach
    // ERROR forEachMetric != Seller


}
