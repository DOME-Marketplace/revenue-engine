package it.eng.dome.revenue.engine.service.validation;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import it.eng.dome.revenue.engine.model.BundleOperator;
import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RecurringPeriod;

//TODO: check other possible validations

public class PlanValidator {

    public PlanValidationReport validate(Plan plan) {
        PlanValidationReport report = new PlanValidationReport();
        report.addIssues(validatePlanMetadata(plan));
        return report;
    }

    private List<PlanValidationIssue> validatePlanMetadata(Plan plan) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        issues.addAll(validateCommonProperties(plan));
        issues.addAll(validateBillingCycleSection(plan));
        issues.addAll(validateAdvancedBillingProperties(plan));

        if (plan.getPrice() != null) {
            issues.addAll(validatePrice(plan.getPrice()));
        }

        return issues;
    }
    
    private List<PlanValidationIssue> validateCommonProperties(Plan plan) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (plan.getName() == null || plan.getName().isEmpty())
            issues.add(new PlanValidationIssue("the plan must have a name", PlanValidationIssueSeverity.WARNING));
        if (plan.getDescription() == null || plan.getDescription().isEmpty())
            issues.add(new PlanValidationIssue("the plan must include a description", PlanValidationIssueSeverity.WARNING));
        if (plan.getValidFor() == null || plan.getValidFor().getStartDateTime().isAfter(plan.getValidFor().getEndDateTime()))
			issues.add(new PlanValidationIssue("the plan must include a validFor period with startDateTime before endDateTime", PlanValidationIssueSeverity.ERROR));
        if (plan.getLifecycleStatus() == null || plan.getLifecycleStatus().isEmpty())
            issues.add(new PlanValidationIssue("the plan must include a lifecycle status", PlanValidationIssueSeverity.ERROR));
        if(plan.getContractDurationLength() != null && plan.getContractDurationLength() < 0)
			issues.add(new PlanValidationIssue("contractDurationLength must be >= 0", PlanValidationIssueSeverity.WARNING));
        return issues;
    }

    private List<PlanValidationIssue> validateBillingCycleSection(Plan plan) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (plan.getBillCycleSpecification().getBillingPeriodLength() == null)
            issues.add(new PlanValidationIssue("the plan must include a billingPeriodLength (an integer)", PlanValidationIssueSeverity.ERROR));
        if (plan.getBillCycleSpecification().getBillingPeriodType() == null)
            issues.add(new PlanValidationIssue("the plan must include a billingPeriodType (i.e. YEAR, MONTH, WEEK, DAY)", PlanValidationIssueSeverity.ERROR));
        if (plan.getBillCycleSpecification().getBillingPeriodEnd() == null)
            issues.add(new PlanValidationIssue("the plan does not provide a 'billingPeriodEnd'", PlanValidationIssueSeverity.WARNING));
        return issues;
    }

    private List<PlanValidationIssue> validateAdvancedBillingProperties(Plan plan) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (plan.getBillCycleSpecification().getBillingDateShift() != null && plan.getBillCycleSpecification().getBillingDateShift() < 0)
            issues.add(new PlanValidationIssue("billingDateShift must be >= 0", PlanValidationIssueSeverity.WARNING));
        if (plan.getBillCycleSpecification().getPaymentDueDateOffset() != null && plan.getBillCycleSpecification().getPaymentDueDateOffset() < 0)
            issues.add(new PlanValidationIssue("paymentDueDateOffset must be >= 0", PlanValidationIssueSeverity.WARNING));
        if(plan.getBillCycleSpecification().getBillingPeriodLength() != null && plan.getBillCycleSpecification().getBillingPeriodLength() < 0)
			issues.add(new PlanValidationIssue("billingPeriodLength must be >= 0", PlanValidationIssueSeverity.WARNING));
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItem(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        issues.addAll(validatePlanItemCommon(item));
        issues.addAll(validatePlanItemApplicable(item));
        issues.addAll(validatePlanItemComputation(item));
        issues.addAll(validatePlanItemIgnore(item));
        issues.addAll(validatePlanItemAmounts(item));
        issues.addAll(validatePlanItemChargeProperties(item));
        issues.addAll(validatePlanItemBundle(item));
        issues.addAll(validatePlanItemForEach(item));
        issues.addAll(validatePlanItemChildItems(item));
        issues.addAll(validatePlanItemConditional(item));
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemCommon(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item.getName() == null || item.getName().isEmpty())
            issues.add(new PlanValidationIssue("PlanItem must have a name", PlanValidationIssueSeverity.WARNING));
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemApplicable(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item.getApplicableBase() != null && item.getApplicableBase().isEmpty())
            issues.add(new PlanValidationIssue("applicableBase is empty", PlanValidationIssueSeverity.ERROR));
        if (item.getApplicableBaseRange() != null &&
            item.getApplicableBaseRange().getMax() != null &&
            item.getApplicableBaseRange().getMin() != null &&
            item.getApplicableBaseRange().getMax() < item.getApplicableBaseRange().getMin())
            issues.add(new PlanValidationIssue("applicableBaseRange min > max", PlanValidationIssueSeverity.ERROR));
        if (item.getApplicableBaseReferencePeriod() != null && item.getApplicableBaseReferencePeriod().toString().isEmpty())
            issues.add(new PlanValidationIssue("applicableBaseReferencePeriod is empty", PlanValidationIssueSeverity.WARNING));
        if (item.getApplicableFrom() != null && item.getApplicableFrom().toString().isEmpty())
            issues.add(new PlanValidationIssue("applicableFrom is empty", PlanValidationIssueSeverity.WARNING));
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemComputation(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item.getComputationBaseReferencePeriod() != null && item.getComputationBaseReferencePeriod().toString().isEmpty())
            issues.add(new PlanValidationIssue("computationBaseReferencePeriod is empty", PlanValidationIssueSeverity.WARNING));
        if (item.getComputationBase() != null && item.getComputationBase().isEmpty())
            issues.add(new PlanValidationIssue("computationBase is empty", PlanValidationIssueSeverity.WARNING));
        if (item.getComputationFrom() != null && item.getApplicableFrom() != null &&
            item.getComputationFrom().isBefore(item.getApplicableFrom()))
            issues.add(new PlanValidationIssue("computationFrom is before applicableFrom", PlanValidationIssueSeverity.WARNING));
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemIgnore(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item.getIgnore() != null && item.getIgnore().isEmpty())
            issues.add(new PlanValidationIssue("Ignore property is empty", PlanValidationIssueSeverity.INFO));
        if (item.getIgnorePeriod() != null && item.getIgnorePeriod().toString().isEmpty())
            issues.add(new PlanValidationIssue("ignorePeriod is empty", PlanValidationIssueSeverity.INFO));
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemAmounts(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item instanceof Price) {
            Price price = (Price) item;
            if (price.getAmount() != null && price.getAmount() < 0)
                issues.add(new PlanValidationIssue("Price amount must be >= 0", PlanValidationIssueSeverity.ERROR));
            if (price.getCurrency() == null || price.getCurrency().isEmpty())
                issues.add(new PlanValidationIssue("Price must have a currency", PlanValidationIssueSeverity.ERROR));
        } else if (item instanceof Discount) {
            Discount discount = (Discount) item;
            if (discount.getPercent() != null && (discount.getPercent() < 0 || discount.getPercent() > 100))
                issues.add(new PlanValidationIssue("Discount percent must be between 0 and 100", PlanValidationIssueSeverity.ERROR));
            if (discount.getAmount() != null && discount.getAmount() < 0)
                issues.add(new PlanValidationIssue("Discount amount must be >= 0", PlanValidationIssueSeverity.ERROR));
        }
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemChargeProperties(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item instanceof Price) {
            Price price = (Price) item;
            if (price.getRecurringChargePeriodLength() != null && price.getRecurringChargePeriodLength() <= 0)
                issues.add(new PlanValidationIssue("RecurringChargePeriodLength must be > 0", PlanValidationIssueSeverity.ERROR));
            if (price.getRecurringChargePeriodType() != null) {
                boolean valid = Arrays.stream(RecurringPeriod.values())
                                      .anyMatch(p -> p == price.getRecurringChargePeriodType());
                if (!valid) {
                    issues.add(new PlanValidationIssue(
                            "Invalid RecurringChargePeriodType: " + price.getRecurringChargePeriodType() +
                            ". Allowed values: " + Arrays.toString(RecurringPeriod.values()),
                            PlanValidationIssueSeverity.ERROR));
                }
            }
        }
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemBundle(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item.getIsBundle()) {
            if (item.getBundleOp() == null)
                issues.add(new PlanValidationIssue("Bundle item must have bundleOp defined", PlanValidationIssueSeverity.ERROR));

            if (item.getBundleItems() != null) {
                for (PlanItem child : item.getBundleItems()) {
                    issues.addAll(validatePlanItem(child));
                }
            }
        }
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemForEach(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item.getBundleOp() == BundleOperator.FOREACH && (item.getForEachMetric() == null || item.getForEachMetric().isEmpty()))
            issues.add(new PlanValidationIssue("Bundle is FOREACH but forEachMetric is empty", PlanValidationIssueSeverity.ERROR));
        if (item.getForEachMetric() != null && !item.getForEachMetric().isEmpty() && item.getBundleOp() != BundleOperator.FOREACH)
            issues.add(new PlanValidationIssue("forEachMetric is defined but bundleOp is not FOREACH", PlanValidationIssueSeverity.WARNING));
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemChildItems(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item.getChildItems() != null) {
            for (PlanItem child : item.getChildItems()) {
                issues.addAll(validatePlanItem(child));
            }
        }
        return issues;
    }

    private List<PlanValidationIssue> validatePlanItemConditional(PlanItem item) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        if (item.isConditional()) {
            boolean hasValue = item.getPercent() != null || item.getApplicableBase() != null || item.getApplicableBaseRange() != null;
            boolean isAtomicItem = !(item instanceof Price && ((Price)item).getPrices() != null)
                                && !(item instanceof Discount && ((Discount)item).getDiscounts() != null);
            if (!hasValue && isAtomicItem)
                issues.add(new PlanValidationIssue("Item marked as conditional but has no applicableBase, range or percent", PlanValidationIssueSeverity.WARNING));
        }
        return issues;
    }

    private List<PlanValidationIssue> validatePrice(Price price) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        issues.addAll(validatePlanItem(price));

        if (price.getPrices() != null) {
            for (Price p : price.getPrices()) {
                if (p.getType() == null) p.setType(price.getType());
                if (p.getRecurringChargePeriodType() == null) p.setRecurringChargePeriodType(price.getRecurringChargePeriodType());
                if (p.getRecurringChargePeriodLength() == null) p.setRecurringChargePeriodLength(price.getRecurringChargePeriodLength());
                issues.addAll(validatePrice(p));
            }
        }

        if (price.getDiscount() != null) {
            issues.addAll(validateDiscount(price.getDiscount()));
        }

        return issues;
    }

    private List<PlanValidationIssue> validateDiscount(Discount discount) {
        List<PlanValidationIssue> issues = new ArrayList<>();
        issues.addAll(validatePlanItem(discount));

        if (discount.getDiscounts() != null) {
            for (Discount d : discount.getDiscounts()) {
                issues.addAll(validateDiscount(d));
            }
        }

        return issues;
    }
}
