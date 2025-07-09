package it.eng.dome.revenue.engine.service.compute;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.model.TimePeriod;
import it.eng.dome.revenue.engine.service.MetricsRetriever;

@Service
public class PriceCalculator {
    private static final Logger logger = LoggerFactory.getLogger(PriceCalculator.class);

    @Autowired
    private MetricsRetriever metricsRetriever;
    private Subscription subscription;

    public Subscription getSubscription() {
        logger.debug("Getting current subscription");
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        logger.info("Setting new subscription: {}", subscription != null ? subscription.getId() : "null");
        this.subscription = subscription;
    }

    public RevenueStatement compute(OffsetDateTime time) {
        logger.info("Computing revenue statement for time: {}", time);

        if (subscription == null || subscription.getPlan() == null) {
            logger.error("Cannot compute - subscription or plan is null");
        }

        try {
            TimePeriod period = new SubscriptionTimeHelper(subscription).getSubscriptionPeriodAt(time);
            logger.debug("Computed period: {} to {}", period.getFromDate(), period.getToDate());

            RevenueStatement statement = new RevenueStatement(subscription, period);
            Price price = subscription.getPlan().getPrice();

            logger.debug("Starting price computation for plan: {}", subscription.getPlan().getName());
            RevenueItem revenueItem = compute(price, time);

            statement.setRevenueItem(revenueItem);
            logger.info("Successfully computed revenue statement with total value: {}", revenueItem.getOverallValue());

            return statement;
        } catch (Exception e) {
            logger.error("Error computing revenue statement: {}", e.getMessage(), e);
        }
        return null;
    }

    public RevenueItem compute(Price price, OffsetDateTime time) {
        logger.debug("Computing price item: {}", price.getName());
        RevenueItem item = new RevenueItem(price.getName(), 0.0, "EUR");

        if (Boolean.TRUE.equals(price.getIsBundle())) {
            logger.debug("Processing bundle price with operation: {}", price.getBundleOp());
            RevenueItem subItem;
            switch (price.getBundleOp()) {
                case CUMULATIVE:
                    subItem = getCumulativePrice(price.getPrices(), time);
                    break;
                case ALTERNATIVE_HIGHER:
                    subItem = getHigherPrice(price.getPrices(), time);
                    break;
                case ALTERNATIVE_LOWER:
                    subItem = getLowerPrice(price.getPrices(), time);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown bundle operation: " + price.getBundleOp());
            }
            item.setItems(List.of(subItem));
            item.setValue(subItem.getOverallValue());
        } else {
            logger.debug("Processing atomic price");
            RevenueItem atomicItem = getAtomicPrice(price, time);
            item.setItems(new ArrayList<>(List.of(atomicItem)));
            item.setValue(atomicItem.getOverallValue());

            if (price.getDiscount() != null && price.getDiscount().getDiscounts() != null && !price.getDiscount().getDiscounts().isEmpty()) {
                logger.info("Applying discounts to price");
                DiscountCalculator discountCalculator = new DiscountCalculator(subscription);
                Double discountValue = discountCalculator.compute(price.getDiscount(), time);
                logger.debug("PRE SCONTO get overall value: {}", item.getOverallValue());

                if (discountValue != null && discountValue > 0) {
                    Double discountAmount = item.getOverallValue() * (discountValue / 100);
                    List<RevenueItem> discountItems = new ArrayList<>();
                    for (Discount discount : price.getDiscount().getDiscounts()) {
                        // Aggiungiamo ogni sconto come item con valore negativo
                        RevenueItem discountItem = new RevenueItem(discount.getName(), -discountAmount, "EUR");
                        discountItems.add(discountItem);
                        logger.debug("Added discount item: {} with value {}", discount.getName(), -discountAmount);
                    }
                    item.getItems().addAll(discountItems);

                    // Ricalcoliamo il valore totale dopo sconto
                    item.setValue(item.getOverallValue());
                    logger.debug("Applied discounts, new overall value: {}", item.getOverallValue());
                } else {
                    logger.debug("Discount value is zero or null, no discounts applied");
                }
            } else {
                logger.debug("No discounts applied to price");
            }
        }

        logger.debug("Final computed price item: {}", item.getOverallValue());
        return item;
    }


    private RevenueItem getAtomicPrice(Price price, OffsetDateTime time) {
        logger.debug("Computing atomic price for: {}", price.getName());

        try {
            SubscriptionTimeHelper sth = new SubscriptionTimeHelper(subscription);

            @Nonnull
			TimePeriod tp = null;
            if (price.getType() != null) {
                switch (price.getType()) {
                    case RECURRING_PREPAID:
                        tp = sth.getSubscriptionPeriodAt(time);
                        logger.debug("Using CURRENT subscription period for RECURRING_PREPAID");
                        break;
                    case RECURRING_POSTPAID:
                        tp = sth.getPreviousSubscriptionPeriod(time);
                        logger.debug("Using PREVIOUS subscription period for RECURRING_POSTPAID");
                        break;
                    case ONE_TIME_PREPAID:
                    	
                        TimePeriod currentPeriod = sth.getSubscriptionPeriodAt(time);
                        OffsetDateTime startDate = subscription.getStartDate();
                        
                        if (currentPeriod.getFromDate().equals(startDate)) {
                            logger.debug("Using CURRENT subscription period for ONE_TIME_PREPAID");
                            tp = currentPeriod;
                        } else {
                            logger.debug("Skipping ONE_TIME_PREPAID - not first period");
                            return new RevenueItem(price.getName(), 0.0, "EUR");
                        }
                        break;
                }
            } else {
                tp = sth.getSubscriptionPeriodAt(time);
                logger.warn("Price type is null, defaulting to CURRENT period");
            }

            logger.debug("Computed time period: {} to {}", tp.getFromDate(), tp.getToDate());

            String sellerId = subscription.getRelatedParties().stream()
                    .filter(rp -> "BUYER".equalsIgnoreCase(rp.getRole()))
                    .findFirst()
                    .orElseThrow(() -> {
                        logger.error("No BUYER found in related parties");
                        return new IllegalStateException("No BUYER found in related parties");
                    })
                    .getId();

            logger.debug("Using seller ID: {}", sellerId);

            Double computedValue = metricsRetriever.computeValueForKey(
                    price.getComputationBase(),
                    sellerId,
                    tp.getFromDate(),
                    tp.getToDate());
            logger.debug("Computed base value: {}", computedValue);

            RevenueItem item = new RevenueItem(price.getName(), computedValue, "EUR");

            if (price.getApplicableBaseRange() != null) {
                Double min = price.getApplicableBaseRange().getMin();
                Double max = price.getApplicableBaseRange().getMax();
                logger.debug("Checking range constraints: min={}, max={}", min, max);

                if ((min != null && computedValue < min) || (max != null && computedValue > max)) {
                    logger.warn("Value {} outside range, setting to 0", computedValue);
                    item.setValue(0.0);
                }
            }

            return item;
        } catch (Exception e) {
            logger.error("Error computing atomic price: {}", e.getMessage(), e);
            return new RevenueItem("Error", 0.0, "EUR");
        }
    }

    private RevenueItem getCumulativePrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Computing cumulative price from {} items", prices.size());
        RevenueItem cumulativeItem = new RevenueItem("Cumulative Price", 0.0, "EUR");

        for (Price p : prices) {
            RevenueItem current = compute(p, time);
            cumulativeItem.addRevenueItem(p.getName(), current.getOverallValue(), "EUR");
            logger.trace("Added item: {} = {}", p.getName(), current.getOverallValue());
        }

        logger.debug("Total cumulative value: {}", cumulativeItem.getOverallValue());
        return cumulativeItem;
    }

    private RevenueItem getHigherPrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Finding higher price from {} items", prices.size());
        RevenueItem higherItem = null;

        for (Price p : prices) {
            RevenueItem current = compute(p, time);
            logger.trace("Comparing item: {} = {}", p.getName(), current.getOverallValue());

            if (higherItem == null || current.getOverallValue() > higherItem.getOverallValue()) {
                higherItem = current;
            }
        }

        if (higherItem == null) {
            logger.warn("No valid prices found, returning 0");
            return new RevenueItem("Higher Price", 0.0, "EUR");
        }

        logger.debug("Selected higher price: {}", higherItem.getOverallValue());
        return higherItem;
    }

    private RevenueItem getLowerPrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Finding lower price from {} items", prices.size());
        RevenueItem lowerItem = null;

        for (Price p : prices) {
            RevenueItem current = compute(p, time);
            logger.trace("Comparing item: {} = {}", p.getName(), current.getOverallValue());

            if (lowerItem == null || current.getOverallValue() < lowerItem.getOverallValue()) {
                lowerItem = current;
            }
        }

        if (lowerItem == null) {
            logger.warn("No valid prices found, returning 0");
            return new RevenueItem("Lower Price", 0.0, "EUR");
        }

        logger.debug("Selected lower price: {}", lowerItem.getOverallValue());
        return lowerItem;
    }
}
