package it.eng.dome.revenue.engine.service.compute;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
            return null;
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

        if (Boolean.TRUE.equals(price.getIsBundle()) && price.getPrices() != null) {
            logger.debug("Processing bundle price with operation: {}", price.getBundleOp());
            List<Price> childPrices = price.getPrices();
            RevenueItem bundleResult;

            switch (price.getBundleOp()) {
                case CUMULATIVE:
                    bundleResult = getCumulativePrice(childPrices, time);
                    break;
                case ALTERNATIVE_HIGHER:
                    bundleResult = getHigherPrice(childPrices, time);
                    break;
                case ALTERNATIVE_LOWER:
                    bundleResult = getLowerPrice(childPrices, time);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown bundle operation: " + price.getBundleOp());
            }

            item.setItems(bundleResult.getItems());
            item.setBundleOp(bundleResult.getBundleOp()); 

        } else {
            logger.debug("Processing atomic price");
            item = getAtomicPrice(price, time);
        }

        if (price.getDiscount() != null) {
            List<RevenueItem> discountItems = new ArrayList<>();

            Double fixedFee = price.getAmount(); //TODO implement fixed fee logic
            DiscountCalculator discountCalculator = new DiscountCalculator(subscription, metricsRetriever);
            RevenueItem discountItem = discountCalculator.compute(price.getDiscount(), time, fixedFee);
            if (discountItem != null) {
                discountItems.add(discountItem);
            }

            if (!discountItems.isEmpty()) {
                if (item.getItems() == null) {
                    item.setItems(new ArrayList<>());
                }
                item.getItems().addAll(discountItems);
            }
        }

        return item;
    }

    
    
    private RevenueItem getAtomicPrice(Price price, OffsetDateTime time) {
        logger.debug("Computing atomic price for: {}", price.getName());

        try {
            SubscriptionTimeHelper sth = new SubscriptionTimeHelper(subscription);

			TimePeriod tp = new TimePeriod();

            if (price.getType() != null) {
                switch (price.getType()) {
                    case RECURRING_PREPAID:
                        tp = sth.getSubscriptionPeriodAt(time);
                        break;
                    case RECURRING_POSTPAID:
                        tp = sth.getPreviousSubscriptionPeriod(time);
                        break;
                    case ONE_TIME_PREPAID:
                        TimePeriod currentPeriod = sth.getSubscriptionPeriodAt(time);
                        OffsetDateTime startDate = subscription.getStartDate();
                        if (currentPeriod.getFromDate().equals(startDate)) {
                            tp = currentPeriod;
                        } else {
                            return new RevenueItem(price.getName(), 0.0, "EUR");
                        }
                        break;
                }
            } else {
            	// TODO: GET PARENT TYPE FROM PRICE 
                tp = sth.getSubscriptionPeriodAt(time);
            }


            String buyerId = subscription.getBuyerId();
            Double computedValue = 0.0;
            Double fee = 0.0;

            if (price.getApplicableBase() != null && !price.getApplicableBase().isEmpty()) {
                computedValue = metricsRetriever.computeValueForKey(price.getApplicableBase(), buyerId, tp.getFromDate(), tp.getToDate());
                computedValue += 200000.00; // Simulating a base value for testing purposes
                if(price.getApplicableBaseRange().getMax()==null) {
                	price.getApplicableBaseRange().setMax(Double.POSITIVE_INFINITY);
				}
					
				
                if (computedValue >= price.getApplicableBaseRange().getMin()
                        && computedValue <= price.getApplicableBaseRange().getMax() ) {

                    if (price.getPercent() != null) {
                        fee = (computedValue * (price.getPercent() / 100));
                    } else if (price.getAmount() != null) {
                        fee = price.getAmount();
                    }
                }
            } else {
                fee = price.getAmount();
            }

            return new RevenueItem(price.getName(), fee, "EUR");

        } catch (Exception e) {
            throw new RuntimeException("Error computing atomic price: " + e.getMessage(), e);
        }
    }

    private RevenueItem getCumulativePrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Computing cumulative price from {} items", prices.size());
        RevenueItem cumulativeItem = new RevenueItem("Cumulative Price", 0.0, "EUR");
        cumulativeItem.setItems(new ArrayList<>());

        for (Price p : prices) {
            RevenueItem current = compute(p, time);
            cumulativeItem.getItems().add(current);
        }

        return cumulativeItem;
    }

    private RevenueItem getHigherPrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Finding higher price from {} items", prices.size());
        RevenueItem higherItem = null;

        for (Price p : prices) {
            RevenueItem current = compute(p, time);
            if (higherItem == null || current.getOverallValue() > higherItem.getOverallValue()) {
                higherItem = current;
            }
        }

        if (higherItem == null) {
            return new RevenueItem("Higher Price", 0.0, "EUR");
        }

        RevenueItem wrapper = new RevenueItem("Higher Price", higherItem.getOverallValue(), "EUR");
        wrapper.setItems(List.of(higherItem));
        return wrapper;
    }

    private RevenueItem getLowerPrice(List<Price> prices, OffsetDateTime time) {
        logger.debug("Finding lower price from {} items", prices.size());
        RevenueItem lowerItem = null;

        for (Price p : prices) {
            RevenueItem current = compute(p, time);
            if (lowerItem == null || current.getOverallValue() < lowerItem.getOverallValue()) {
                lowerItem = current;
            }
        }

        if (lowerItem == null) {
            return new RevenueItem("Lower Price", 0.0, "EUR");
        }

        RevenueItem wrapper = new RevenueItem("Lower Price", lowerItem.getOverallValue(), "EUR");
        wrapper.setItems(List.of(lowerItem));
        return wrapper;
    }
}
