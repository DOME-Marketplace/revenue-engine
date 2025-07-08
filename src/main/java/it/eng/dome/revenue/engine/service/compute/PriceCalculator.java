package it.eng.dome.revenue.engine.service.compute;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.model.TimePeriod;
import it.eng.dome.revenue.engine.service.MetricsRetriever;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO IMPLEMENT LOGIC FOR TAX BRACKETS


@Service
public class PriceCalculator {

    @Autowired
    private MetricsRetriever metricsRetriever;

    private static final Logger logger = LoggerFactory.getLogger(PriceCalculator.class);

    private Subscription subscription;

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
        logger.debug("Subscription set: {}", subscription);
    }

    public Double compute(OffsetDateTime time) {
        if (subscription == null || subscription.getPlan() == null) {
            logger.error("Subscription or SubscriptionPlan is null");
            return 0.0;
        }
        logger.debug("Computing price for time: {}", time);
        return this.compute(subscription.getPlan().getPrice(), time);
    }

    public Double compute(Price price, OffsetDateTime time) {
        if (price == null) {
            logger.error("Price is null");
            return 0.0;
        }

        logger.debug("Computing price at time: {}, isBundle: {}", time, price.getIsBundle());

        Double priceValue = 0.0;
        Double discountValue = 0.0;
        Double fixedFee = 0.0; 
        if (Boolean.TRUE.equals(price.getIsBundle())) {
            logger.debug("Bundle operation: {}", price.getBundleOp());

            switch (price.getBundleOp()) {
                case CUMULATIVE:
                    priceValue = this.getCumulativePrice(price.getPrices(), time);
                    break;
                case ALTERNATIVE_HIGHER:
                    priceValue = this.getHigherPrice(price.getPrices(), time);
                    break;
                case ALTERNATIVE_LOWER:
                    priceValue = this.getLowerPrice(price.getPrices(), time);
                    break;
                default:
                    logger.error("Unknown bundle operation: {}", price.getBundleOp());
                    throw new IllegalArgumentException("Unknown bundle operation: " + price.getBundleOp());
            }
        } else {
            logger.debug("Computing atomic price");
            priceValue = this.getAtomicPrice(price, time);
        }

        if (price.getDiscount() != null && !price.getDiscount().getDiscounts().isEmpty()) {
            logger.info("Applying discount");
            DiscountCalculator discountCalculator = new DiscountCalculator(subscription);
            // placeholder discount logic
            
            discountValue = discountCalculator.compute(price.getDiscount(), time); // volume sales parameter????
            // to apply the discount to fixed fee, we assume the discount is a percentage
            fixedFee = price.getAmount();
            fixedFee -= discountValue/100.0 * fixedFee;
            
			logger.info("Discount applied: {}, Fixed Fee next year (discounted): {}", discountValue, fixedFee);
        }

        return priceValue;
    }

    private Double getAtomicPrice(Price price, OffsetDateTime time) {
        logger.debug("getAtomicPrice - price name: {}, computationBase: {}", price.getName(), price.getComputationBase());

        SubscriptionTimeHelper sth = new SubscriptionTimeHelper(this.subscription);
        TimePeriod tp = null;

        try {
            tp = sth.getSubscriptionPeriodAt(time);
            logger.debug("Calculated subscription time period: from {} to {}", tp.getFromDate(), tp.getToDate());
        } catch (Exception e) {
            logger.error("Error while calculating subscription time period: ", e);
            return 0.0;
        }

        String sellerId = null;
        try {
            sellerId = subscription.getRelatedParties().stream()
                    .filter(rp -> "BUYER".equalsIgnoreCase(rp.getRole()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No BUYER found in related parties"))
                    .getId();
        } catch (Exception e) {
            logger.error("Error finding BUYER in relatedParties", e);
            return 0.0;
        }

        logger.debug("Found sellerId (BUYER): {}", sellerId);

        Double computedValue = 0.0;
        String computationBase = price.getComputationBase();

        try {
            computedValue = metricsRetriever.computeValueForKey(
                    computationBase, sellerId, tp.getFromDate(), tp.getToDate());
            logger.debug("Computed metric value: {}", computedValue);
        } catch (Exception e) {
            logger.error("Error computing metric value", e);
            return 0.0;
        }

//        if (price.getApplicableBaseRange() != null) {
//            Double min = price.getApplicableBaseRange().getMin();
//            Double max = price.getApplicableBaseRange().getMax();
//            logger.debug("Checking applicableBaseRange: min={}, max={}, computed={}", min, max, computedValue);
//        }

        return computedValue;
    }

    private Double getCumulativePrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Calculating cumulative price...");
        Double cumulativePrice = 0.0;
        for (Price p : prices) {
            cumulativePrice += this.compute(p, time);
        }
        logger.info("Cumulative price calculated: {}", cumulativePrice);
        return cumulativePrice;
    }

    private Double getHigherPrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Calculating higher price...");
        Double higher = Double.MIN_VALUE;
        for (Price p : prices) {
            Double pValue = this.compute(p, time);
            higher = Math.max(higher, pValue);
        }
        logger.info("Higher price calculated: {}", higher);
        return higher;
    }

    private Double getLowerPrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Calculating lower price...");
        Double lower = Double.MAX_VALUE;
        for (Price p : prices) {
            Double pValue = this.compute(p, time);
            lower = Math.min(lower, pValue);
        }
        logger.info("Lower price calculated: {}", lower);
        return lower;
    }
}