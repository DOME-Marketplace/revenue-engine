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
import it.eng.dome.revenue.engine.service.MetricsRetriever;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class DiscountCalculator {
    private static final Logger logger = LoggerFactory.getLogger(DiscountCalculator.class);

    @Autowired
    private MetricsRetriever metricsRetriever;

    private Subscription subscription;

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

    public RevenueItem compute(Discount discount, TimePeriod timePeriod, Double amount) {
        logger.debug("Computing discount item: {}", discount.getName());

        if (Boolean.TRUE.equals(discount.getIsBundle()) && discount.getDiscounts() != null) {
            RevenueItem bundleResult = getBundleDiscount(discount, timePeriod, amount);
            return bundleResult;
        } else {
            RevenueItem atomicDiscount = getAtomicDiscount(discount, timePeriod, amount);
            if (atomicDiscount == null) {
                logger.info("Discount {} not applicable (atomic discount is null), skipping item creation.", discount.getName());
                return null;
            }
            return atomicDiscount;
        }
    }

    private RevenueItem getBundleDiscount(Discount discount, TimePeriod timePeriod, Double amount) {
        logger.debug("Processing bundle discount with operation: {}", discount.getBundleOp());
        RevenueItem bundleResult;

        switch (discount.getBundleOp()) {
            case CUMULATIVE:
                bundleResult = getCumulativeDiscount(discount, timePeriod, amount);
                break;
            case ALTERNATIVE_HIGHER:
                bundleResult = getHigherDiscount(discount, timePeriod, amount);
                break;
            case ALTERNATIVE_LOWER:
                bundleResult = getLowerDiscount(discount, timePeriod, amount);
                break;
            default:
                throw new IllegalArgumentException("Unknown bundle operation: " + discount.getBundleOp());
        }

        return bundleResult;
    }

    private RevenueItem getAtomicDiscount(Discount discount, TimePeriod timePeriod, Double amount) {
        logger.debug("Computing atomic discount for: {}", discount.getName());

        TimePeriod tp = getTimePeriod(discount, timePeriod.getStartDateTime().plusSeconds(1));
//        TimePeriod tp = getTimePeriod(discount, time);

        if(!tp.getStartDateTime().equals(timePeriod.getStartDateTime()) || !tp.getEndDateTime().equals(timePeriod.getEndDateTime())) {
            logger.debug("Time period mismatch for price {}: REF {}, DISCOUNT TP {}. Skipping this price.", discount.getName(), timePeriod, tp);
            return null;
        }

        String buyerId = subscription.getBuyerId();
        Double amountValue = this.computeDiscount(discount, buyerId, tp, amount);

        if (amountValue == null || amountValue == 0.0) {
            logger.info("Atomic discount for {} is null or zero, returning null", discount.getName());
            return null;
        }

        return new RevenueItem(discount.getName(), -amountValue, "EUR");
    }

    private TimePeriod getTimePeriod(Discount discount, OffsetDateTime time) {
        SubscriptionTimeHelper sth = new SubscriptionTimeHelper(subscription);
        TimePeriod tp;

        if (discount.getApplicableBaseReferencePeriod() != null) {
            if (discount.getApplicableBaseReferencePeriod().getValue().equalsIgnoreCase("PREVIOUS_SUBSCRIPTION_PERIOD")) {
                tp = sth.getSubscriptionPeriodAt(time);
            } else {
            	// TODO: recoding this logic
                tp = null;
            }
        } else {
            // TODO: get by parent reference period ?? - discuss if it can be removed
            tp = sth.getSubscriptionPeriodAt(time);
        }

        return tp;
    }

    private Double computeDiscount(Discount discount, String buyerId, TimePeriod tp, Double amount) {
        Double applicableValue = getApplicableValue(discount, buyerId, tp);

        logger.info("applicable value computed: {}", applicableValue);

        if (applicableValue == null) {
            // if not exists an applicable or an computation then we had only amount discount
            return discount.getAmount();
        }

        // if value in range then computation 
        if (discount.getApplicableBaseRange() != null && discount.getApplicableBaseRange().inRange(applicableValue)) {
            return getComputationValue(discount, buyerId, tp, amount);
        } else if (discount.getApplicableBaseRange() == null) {
            // No range specified, proceed with computation
            return getComputationValue(discount, buyerId, tp, amount);
        } else {
            // when not in range
            logger.info("Not in range {}", applicableValue);
            return null;
        }
    }

    private Double getComputationValue(Discount discount, String buyerId, TimePeriod tp, Double amount) {
        logger.info("Computation of discount value");
        // computation logic
        if (discount.getComputationBase() != null && !discount.getComputationBase().isEmpty()) {
        	 if (discount.getPercent() != null) {
	            Double computationValue = 0.0;
	            try {
	                // Handle special case for "parent-price" computation base
	                if ("parent-price".equals(discount.getComputationBase())) {
	                    computationValue = amount; // Use the parent price amount
	                    logger.info("Using parent price amount: {}", computationValue);
	                } else {
	                    computationValue = metricsRetriever.computeValueForKey(discount.getComputationBase(), buyerId, tp);
	                    //computationValue += 200000.00; // Simulating a base value for testing purposes
	                    logger.info("Computation value computed: {} in tp: {}", computationValue, tp);
	                }
	            } catch (Exception e) {
	                logger.error("Error computing discount value: {}", e.getMessage(), e);
	            }

                return (computationValue * (discount.getPercent() / 100));
            } else if (discount.getAmount() != null) {
                return discount.getAmount();
            }
        } else {
        	// TODO: discuss about this else
            logger.warn("Computation not exists!");
        }
        return null;
    }

    private Double getApplicableValue(Discount discount, String buyerId, TimePeriod tp) {
        Double applicableValue = 0.0;

        // APPLICABLE LOGIC
        if (discount.getApplicableBase() != null && !discount.getApplicableBase().isEmpty()) {
            try {
                applicableValue = metricsRetriever.computeValueForKey(discount.getApplicableBase(), buyerId, tp);
                logger.info("Applicable value computed: {} in tp: {}", applicableValue, tp);
            } catch (Exception e) {
                logger.error("Error getting applicable value: {}", e.getMessage(), e);
            }
            //applicableValue += 200000.00; // Simulating a base value for testing purposes
        } else {
            return null;
        }

        return applicableValue;
    }

    private RevenueItem getCumulativeDiscount(Discount bundleDiscount, TimePeriod timePeriod, Double amount) {
        List<Discount> childDiscounts = bundleDiscount.getDiscounts();
        logger.debug("Computing cumulative discount from {} items", childDiscounts.size());

        RevenueItem cumulativeItem = new RevenueItem(bundleDiscount.getName(), bundleDiscount.getCurrency());
        cumulativeItem.setItems(new ArrayList<>());

        for (Discount d : childDiscounts) {
            RevenueItem current = this.compute(d, timePeriod, amount);
            if (current != null) {
                cumulativeItem.getItems().add(current);
            }
        }

        if (cumulativeItem.getItems().isEmpty()) {
            return null;
        }

        return cumulativeItem;
    }

    private RevenueItem getHigherDiscount(Discount bundleDiscount, TimePeriod timePeriod, Double amount) {
        List<Discount> childDiscounts = bundleDiscount.getDiscounts();
        logger.debug("Finding higher discount from {} items", childDiscounts.size());

        RevenueItem bestItem = null;

        for (Discount d : childDiscounts) {
            RevenueItem current = this.compute(d, timePeriod, amount);
            if (current == null) continue;

            if (bestItem == null || current.getOverallValue() < bestItem.getOverallValue()) {
                bestItem = current;
            }
        }

        if (bestItem == null) return null;

        RevenueItem wrapper = new RevenueItem(bundleDiscount.getName(), bundleDiscount.getCurrency());
        List<RevenueItem> items = new ArrayList<>();
        items.add(bestItem);
        wrapper.setItems(items);

        return wrapper;
    }


    private RevenueItem getLowerDiscount(Discount bundleDiscount, TimePeriod timePeriod, Double amount) {
        List<Discount> childDiscounts = bundleDiscount.getDiscounts();
        logger.debug("Finding lower discount from {} items", childDiscounts.size());

        RevenueItem bestItem = null;

        for (Discount d : childDiscounts) {
            RevenueItem current = compute(d, timePeriod, amount);
            if (current == null) continue;

            if (bestItem == null || current.getOverallValue() > bestItem.getOverallValue()) {
                bestItem = current;
            }
        }

        if (bestItem == null) return null;

        RevenueItem wrapper = new RevenueItem(bundleDiscount.getName(), bundleDiscount.getCurrency());
        List<RevenueItem> items = new ArrayList<>();
        items.add(bestItem);
        wrapper.setItems(items);

        return wrapper;
    }


}