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

    /**
     * Default constructor.
     */
    public DiscountCalculator() {
    }

    /**
     * Constructor with subscription and metrics retriever.
     * 
     * @param subscription the subscription context
     * @param metricsRetriever the retriever for metric values
     */
    public DiscountCalculator(Subscription subscription, MetricsRetriever metricsRetriever) {
        this.subscription = subscription;
        this.metricsRetriever = metricsRetriever;
    }

    /**
     * Sets the metrics retriever.
     * 
     * @param metricsRetriever the retriever for metric values
     */
    public void setMetricsRetriever(MetricsRetriever metricsRetriever) {
        this.metricsRetriever = metricsRetriever;
    }

    /**
     * Sets the subscription.
     * 
     * @param subscription the subscription context
     */
    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    /**
     * Computes the discount item based on the provided discount, time period, and amount.
     * 
     * @param discount the discount to apply
     * @param timePeriod the time period for which the discount is applicable
     * @param amount the amount to which the discount will be applied
     * @return a RevenueItem representing the computed discount, or null if not applicable
     */
    public RevenueItem compute(Discount discount, TimePeriod timePeriod, Double amount) {
        logger.debug("Computing discount item: {}", discount.getName());

        // TODO: support for applicableFrom?
        
        // TODO: support for ignorePeriod?

        if (Boolean.TRUE.equals(discount.getIsBundle()) && discount.getDiscounts() != null) {
            RevenueItem bundleResult = getBundleDiscount(discount, timePeriod, amount);
            return bundleResult;
        } else {
            RevenueItem atomicDiscount = getAtomicDiscount(discount, timePeriod, amount);
            if (atomicDiscount == null) {
                logger.debug("Discount {} not applicable (atomic discount is null), skipping item creation.", discount.getName());
                return null;
            }
            return atomicDiscount;
        }
    }

    /**
     * Processes a bundle discount based on its operation type.
     * 
     * @param discount the bundle discount to process
     * @param timePeriod the time period for which the discount is applicable
     * @param amount the amount to which the discount will be applied
     * @return a RevenueItem representing the computed bundle discount
     */
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

    /**
     * Computes an atomic discount based on the provided discount, time period, and amount.
     * 
     * @param discount the discount to apply
     * @param timePeriod the time period for which the discount is applicable
     * @param amount the amount to which the discount will be applied
     * @return a RevenueItem representing the computed atomic discount, or null if not applicable
     */
    private RevenueItem getAtomicDiscount(Discount discount, TimePeriod timePeriod, Double amount) {
        logger.debug("Computing atomic discount for: {}", discount.getName());

        TimePeriod tp = getTimePeriod(discount, timePeriod.getStartDateTime().plusSeconds(1));

        if(!tp.getStartDateTime().equals(timePeriod.getStartDateTime()) || !tp.getEndDateTime().equals(timePeriod.getEndDateTime())) {
            logger.debug("Time period mismatch for price {}: REF {}, DISCOUNT TP {}. Skipping this price.", discount.getName(), timePeriod, tp);
            return null;
        }

        String buyerId = subscription.getBuyerId();
        Double amountValue = this.computeDiscount(discount, buyerId, tp, amount);

        if (amountValue == null || amountValue == 0.0) {
            logger.debug("Atomic discount for {} is null or zero, returning null", discount.getName());
            return null;
        }

        return new RevenueItem(discount.getName(), -amountValue, "EUR");
    }

    /**
     * Retrieves the time period applicable for the discount based on its reference period.
     * 
     * @param discount the discount definition
     * @param time the offset date time to base the computation on
     * @return the TimePeriod applicable for the discount
     */
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

    /**
     * Computes the discount value based on the discount type, buyer ID, time period, and amount.
     * 
     * @param discount the discount configuration
     * @param buyerId the identifier of the buyer
     * @param tp the applicable time period
     * @param amount the amount to apply the discount on
     * @return the computed discount value, or null if not applicable
     */
    private Double computeDiscount(Discount discount, String buyerId, TimePeriod tp, Double amount) {
        Double applicableValue = getApplicableValue(discount, buyerId, tp);

        if (applicableValue == null) {
            return discount.getAmount();
        }

        if (discount.getApplicableBaseRange() != null && discount.getApplicableBaseRange().inRange(applicableValue)) {
            logger.info("Applicable value computed: {} in tp: {} - {}", applicableValue, tp.getStartDateTime(), tp.getEndDateTime());
            return getComputationValue(discount, buyerId, tp, amount);
        } else if (discount.getApplicableBaseRange() == null) {
            return getComputationValue(discount, buyerId, tp, amount);
        } else {
            return null;
        }
    }

    /**
     * Computes the actual discount value based on the computation base and percent/amount.
     * 
     * @param discount the discount configuration
     * @param buyerId the identifier of the buyer
     * @param tp the applicable time period
     * @param amount the amount to which the discount will be applied
     * @return the discount value computed, or null if not computable
     */
    private Double getComputationValue(Discount discount, String buyerId, TimePeriod tp, Double amount) {
        logger.debug("Computation of discount value");
        if (discount.getComputationBase() != null && !discount.getComputationBase().isEmpty()) {
            if (discount.getPercent() != null) {
                Double computationValue = 0.0;
                try {
                    if ("parent-price".equals(discount.getComputationBase())) {
                        computationValue = amount;
                        logger.debug("Using parent price amount: {}", computationValue);
                    } else {
                        computationValue = metricsRetriever.computeValueForKey(discount.getComputationBase(), buyerId, tp);
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

    /**
     * Computes the applicable value used to determine discount eligibility.
     * 
     * @param discount the discount configuration
     * @param buyerId the identifier of the buyer
     * @param tp the applicable time period
     * @return the applicable value, or null if not available
     */
    private Double getApplicableValue(Discount discount, String buyerId, TimePeriod tp) {
        Double applicableValue = 0.0;

        if (discount.getApplicableBase() != null && !discount.getApplicableBase().isEmpty()) {
            try {
                applicableValue = metricsRetriever.computeValueForKey(discount.getApplicableBase(), buyerId, tp);
            } catch (Exception e) {
                logger.error("Error getting applicable value: {}", e.getMessage(), e);
            }
        } else {
            return null;
        }

        return applicableValue;
    }

    /**
     * Computes a cumulative discount by summing up all valid child discounts.
     * 
     * @param bundleDiscount the parent bundle discount
     * @param timePeriod the applicable time period
     * @param amount the base amount
     * @return a RevenueItem aggregating all applicable discounts, or null if none apply
     */
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

    /**
     * Computes the highest discount among the child discounts (least negative).
     * 
     * @param bundleDiscount the parent bundle discount
     * @param timePeriod the applicable time period
     * @param amount the base amount
     * @return a RevenueItem wrapping the best discount, or null if none apply
     */
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

    /**
     * Computes the lowest discount among the child discounts (most negative).
     * 
     * @param bundleDiscount the parent bundle discount
     * @param timePeriod the applicable time period
     * @param amount the base amount
     * @return a RevenueItem wrapping the worst discount, or null if none apply
     */
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
