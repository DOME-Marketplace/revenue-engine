package it.eng.dome.revenue.engine.service.compute;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.model.TimePeriod;
import it.eng.dome.revenue.engine.service.MetricsRetriever;

public class DiscountCalculator {

    @Autowired
    private MetricsRetriever metricsRetriever;

    private Subscription subscription;

    private static final Logger logger = LoggerFactory.getLogger(DiscountCalculator.class);

    public DiscountCalculator() {
    }

    public DiscountCalculator(Subscription subscription, MetricsRetriever metricsRetriever) {
        this.subscription = subscription;
        this.metricsRetriever = metricsRetriever;
    }

    public void setMetricsRetriever(MetricsRetriever metricsRetriever) {
		this.metricsRetriever = metricsRetriever;
	}
    
    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    /**
     * Compute a Discount into a RevenueItem, recursively managing bundles.
     */
    //TODO: handle cases where discount is referred-base
    
    public RevenueItem compute(Discount discount, OffsetDateTime time, Double fixedFee) {
        if (discount == null) return null;

        if (Boolean.TRUE.equals(discount.getIsBundle())) {
            List<Discount> childDiscounts = discount.getDiscounts();
            if (childDiscounts == null || childDiscounts.isEmpty()) {
                return new RevenueItem(discount.getName(), 0.0, "EUR");
            }

            RevenueItem bundleResult;

            switch (discount.getBundleOp()) {
                case CUMULATIVE:
                    bundleResult = getCumulativeDiscount(childDiscounts, time, fixedFee);
                    break;
                case ALTERNATIVE_HIGHER:
                    bundleResult = getHigherDiscount(childDiscounts, time, fixedFee);
                    break;
                case ALTERNATIVE_LOWER:
                    bundleResult = getLowerDiscount(childDiscounts, time, fixedFee);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown bundle operation: " + discount.getBundleOp());
            }

            RevenueItem bundleItem = new RevenueItem(discount.getName(), 0.0, "EUR");
            bundleItem.setItems(bundleResult.getItems());
            
            return bundleItem;

        } else {
            // Atomic discount: compute base value and apply percent/amount

            try {
                SubscriptionTimeHelper sth = new SubscriptionTimeHelper(subscription);
                TimePeriod tp;

                if (discount.getApplicableBaseReferencePeriod() != null) {
                	
                	// TODO IMPLEMENT SWITCH CASE
                	tp = sth.getSubscriptionPeriodAt(time);
                }else {
                	//TODO get by parent
                	tp = sth.getSubscriptionPeriodAt(time);

                }


                logger.debug("Computing atomic discount '{}' for period {} - {}", discount.getName(), tp.getFromDate(), tp.getToDate());

                String buyerId = subscription.getBuyerId();
                double computedValue = 0.0;
                
                // FIXME: fix this logic to retrieve the base value correctly
                if (discount.getApplicableBase() != null && !discount.getApplicableBase().isEmpty()) {
                    // Compute base value from metricsRetriever for given base key and period
                	computedValue = metricsRetriever.computeValueForKey(discount.getApplicableBase(), buyerId, tp.getFromDate(), tp.getToDate());
                }
                
                computedValue += 200000.0; // Add a fixed base value for testing purposes
                // Apply range check if present
                boolean inRange = true;
                if (discount.getApplicableBaseRange() != null) {
                    Double min = discount.getApplicableBaseRange().getMin() != null ? discount.getApplicableBaseRange().getMin() : 0.0;
                    Double max = discount.getApplicableBaseRange().getMax() != null ? discount.getApplicableBaseRange().getMax() : Double.POSITIVE_INFINITY;
                    inRange = (computedValue >= min) && (computedValue <= max);
                }

                double discountValue = 0.0;
                if (inRange) {
                    if (discount.getPercent() != null) {

                        discountValue = fixedFee * (discount.getPercent() / 100.0);
                    } else if (discount.getAmount() != null) {
                        discountValue = discount.getAmount();
                    } else {
                        // no percent or amount, zero discount
                        discountValue = 0.0;
                    }
                }

                return new RevenueItem(discount.getName(), -discountValue, "EUR");

            } catch (Exception e) {
                throw new RuntimeException("Error computing atomic discount: " + e.getMessage(), e);
            }
        }
    }

    private RevenueItem getCumulativeDiscount(List<Discount> discounts, OffsetDateTime time, Double fixedFee) {
        if (discounts == null || discounts.isEmpty()) {
            return new RevenueItem("bundle_cumulative", 0.0, "EUR");
        }
        double cumulativeValue = 0.0;
        List<RevenueItem> children = new ArrayList<>();
        for (Discount d : discounts) {
            RevenueItem childItem = compute(d, time, fixedFee);
            if (childItem != null) {
                cumulativeValue += childItem.getValue();
                children.add(childItem);
            }
        }
        RevenueItem bundleItem = new RevenueItem("bundle_cumulative", cumulativeValue, "EUR");
        bundleItem.setItems(children);
        return bundleItem;
    }

    private RevenueItem getHigherDiscount(List<Discount> discounts, OffsetDateTime time, Double fixedFee) {
        if (discounts == null || discounts.isEmpty()) {
            return new RevenueItem("bundle_higher", 0.0, "EUR");
        }
        List<RevenueItem> children = new ArrayList<>();
        RevenueItem maxItem = null;

        for (Discount d : discounts) {
            RevenueItem item = compute(d, time, fixedFee);
            if (item != null) {
                children.add(item);
                if (maxItem == null || item.getValue() > maxItem.getValue()) {
                    maxItem = item;
                }
            }
        }

        double maxValue = (maxItem != null) ? maxItem.getValue() : 0.0;
        RevenueItem bundleItem = new RevenueItem("bundle_higher", maxValue, "EUR");
        bundleItem.setItems(children);
        return bundleItem;
    }

    private RevenueItem getLowerDiscount(List<Discount> discounts, OffsetDateTime time, Double fixedFee) {
        if (discounts == null || discounts.isEmpty()) {
            return new RevenueItem("bundle_lower", 0.0, "EUR");
        }
        RevenueItem lowerItem = null;
        List<RevenueItem> children = new ArrayList<>();
        for (Discount d : discounts) {
            RevenueItem childItem = compute(d, time, fixedFee);
            if (childItem != null) {
                children.add(childItem);
                if (lowerItem == null || childItem.getValue() < lowerItem.getValue()) {
                    lowerItem = childItem;
                }
            }
        }
        double value = lowerItem != null ? lowerItem.getValue() : 0.0;
        RevenueItem bundleItem = new RevenueItem("bundle_lower", value, "EUR");
        bundleItem.setItems(children);
        return bundleItem;
    }
}
