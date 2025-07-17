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

    public RevenueItem compute(Discount discount, OffsetDateTime time, Double amount) {
        logger.debug("Computing discount item: {}", discount.getName());



        if (Boolean.TRUE.equals(discount.getIsBundle()) && discount.getDiscounts() != null) {
            RevenueItem bundleResult = getBundleDiscount(discount, time, amount);
            return bundleResult;
        } else {
            RevenueItem atomicDiscount = getAtomicDiscount(discount, time, amount);
            if (atomicDiscount == null) {
                logger.info("Discount {} not applicable (atomic discount is null), skipping item creation.", discount.getName());
                return null;
            }
            return atomicDiscount;
        }
    }

    private RevenueItem getBundleDiscount(Discount discount, OffsetDateTime time, Double amount) {
        logger.debug("Processing bundle discount with operation: {}", discount.getBundleOp());
        RevenueItem bundleResult;

        switch (discount.getBundleOp()) {
            case CUMULATIVE:
                bundleResult = getCumulativeDiscount(discount, time, amount);
                break;
            case ALTERNATIVE_HIGHER:
                bundleResult = getHigherDiscount(discount, time, amount);
                break;
            case ALTERNATIVE_LOWER:
                bundleResult = getLowerDiscount(discount, time, amount);
                break;
            default:
                throw new IllegalArgumentException("Unknown bundle operation: " + discount.getBundleOp());
        }

        return bundleResult;
    }

    private RevenueItem getAtomicDiscount(Discount discount, OffsetDateTime time, Double amount) {
        logger.debug("Computing atomic discount for: {}", discount.getName());

        TimePeriod tp = getTimePeriod(discount, time);
        String buyerId = subscription.getBuyerId();
        Double amountValue = computeDiscount(discount, buyerId, tp, amount);

        if (amountValue == null || amountValue == 0.0) {
            logger.info("Atomic discount for {} is null or zero, returning null", discount.getName());
            return null;
        }

        return new RevenueItem(discount.getName(), -amountValue, "EUR");
    }

    private TimePeriod getTimePeriod(Discount discount, OffsetDateTime time) {
        SubscriptionTimeHelper sth = new SubscriptionTimeHelper(subscription);
        TimePeriod tp;
        //TODO: handle this case properly
        if (discount.getApplicableBaseReferencePeriod() != null) {
 
        	tp = sth.getSubscriptionPeriodAt(time);
                   
            
        } else {
            // TODO: get by parent reference period
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
            Double computationValue = 0.0;
            try {
                // Handle special case for "parent-price" computation base
                if ("parent-price".equals(discount.getComputationBase())) {
                    computationValue = amount; // Use the parent price amount
                    logger.info("Using parent price amount: {}", computationValue);
                } else {
                    computationValue = metricsRetriever.computeValueForKey(discount.getComputationBase(), buyerId, tp);
                    computationValue += 200000.00; // Simulating a base value for testing purposes
                    logger.info("Computed value from metrics: {}", computationValue);
                }
            } catch (Exception e) {
                logger.error("Error computing discount value: {}", e.getMessage(), e);
            }

            if (discount.getPercent() != null) {
                return (computationValue * (discount.getPercent() / 100));
            } else if (discount.getAmount() != null) {
                return discount.getAmount();
                // TODO: discutere di come gestire questo amount o percent e sicuro questa non è la posizione per fare questo if
            }

        } else {
            // TODO: logic when computation not exists
            logger.info("computation not exists");
            return 0.0;
        }

        return null;
    }

    private Double getApplicableValue(Discount discount, String buyerId, TimePeriod tp) {
        Double applicableValue = 0.0;

        // APPLICABLE LOGIC
        if (discount.getApplicableBase() != null && !discount.getApplicableBase().isEmpty()) {
            try {
                applicableValue = metricsRetriever.computeValueForKey(discount.getApplicableBase(), buyerId, tp);
            } catch (Exception e) {
                logger.error("Error getting applicable value: {}", e.getMessage(), e);
            }
            applicableValue += 200000.00; // Simulating a base value for testing purposes
        } else {
            return null;
        }

        return applicableValue;
    }

    private RevenueItem getCumulativeDiscount(Discount bundleDiscount, OffsetDateTime time, Double amount) {
        List<Discount> childDiscounts = bundleDiscount.getDiscounts();
        logger.debug("Computing cumulative discount from {} items", childDiscounts.size());

        RevenueItem cumulativeItem = new RevenueItem(bundleDiscount.getName());
        cumulativeItem.setItems(new ArrayList<>());

        for (Discount d : childDiscounts) {
            RevenueItem current = compute(d, time, amount);
            if (current != null) {
                cumulativeItem.getItems().add(current);
            }
        }

        if (cumulativeItem.getItems().isEmpty()) {
            return null;
        }

        return cumulativeItem;
    }

    private RevenueItem getHigherDiscount(Discount bundleDiscount, OffsetDateTime time, Double amount) {
        List<Discount> childDiscounts = bundleDiscount.getDiscounts();
        logger.debug("Finding higher discount from {} items", childDiscounts.size());

        RevenueItem bestItem = null;

        for (Discount d : childDiscounts) {
            RevenueItem current = compute(d, time, amount);
            if (current == null) continue;

            if (bestItem == null || current.getOverallValue() < bestItem.getOverallValue()) {
                bestItem = current;
            }
        }

        if (bestItem == null) return null;

        RevenueItem wrapper = new RevenueItem(bundleDiscount.getName());
        List<RevenueItem> items = new ArrayList<>();
        items.add(bestItem);
        wrapper.setItems(items);

        return wrapper;
    }


    private RevenueItem getLowerDiscount(Discount bundleDiscount, OffsetDateTime time, Double amount) {
        List<Discount> childDiscounts = bundleDiscount.getDiscounts();
        logger.debug("Finding lower discount from {} items", childDiscounts.size());

        RevenueItem bestItem = null;

        for (Discount d : childDiscounts) {
            RevenueItem current = compute(d, time, amount);
            if (current == null) continue;

            if (bestItem == null || current.getOverallValue() > bestItem.getOverallValue()) {
                bestItem = current;
            }
        }

        if (bestItem == null) return null;

        RevenueItem wrapper = new RevenueItem(bundleDiscount.getName());
        List<RevenueItem> items = new ArrayList<>();
        items.add(bestItem);
        wrapper.setItems(items);

        return wrapper;
    }


}