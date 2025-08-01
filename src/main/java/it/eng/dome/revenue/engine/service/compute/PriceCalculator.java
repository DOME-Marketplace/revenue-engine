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
import it.eng.dome.revenue.engine.service.MetricsRetriever;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class PriceCalculator {
	private static final Logger logger = LoggerFactory.getLogger(PriceCalculator.class);

	@Autowired
	private MetricsRetriever metricsRetriever;

	private Subscription subscription;

	public Subscription getSubscription() {
		logger.info("Getting current subscription");
		return subscription;
	}

	public void setSubscription(Subscription subscription) {
		logger.debug("Setting new subscription: {}", subscription != null ? subscription.getId() : "null");
		this.subscription = subscription;
	}

	public RevenueStatement compute(TimePeriod period) {
		logger.debug("Computing revenue statement for time: {}", period);

		if (subscription == null || subscription.getPlan() == null) {
			logger.error("Cannot compute - subscription or plan is null");
			return null;
		}

		try {

			RevenueStatement statement = new RevenueStatement(subscription, period);
			Price price = subscription.getPlan().getPrice();

			RevenueItem revenueItem = this.compute(price, period);
			if (revenueItem == null) {
				logger.error("No revenue item computed for plan: {}", subscription.getPlan().getName());
				return null;
			}
			statement.addRevenueItem(revenueItem);
			logger.info("Successfully computed revenue statement with total value: {}", revenueItem.getOverallValue());

			return statement;
		} catch (Exception e) {
			logger.error("Error computing revenue statement: {}", e.getMessage(), e);
		}

		return null;
	}

//    public RevenueItem compute(Price price, OffsetDateTime time) {
	public RevenueItem compute(Price price, TimePeriod timePeriod) {
		logger.debug("Computing price item: {}", price.getName());

		if (price.getApplicableFrom() != null && timePeriod.getStartDateTime().isBefore(price.getApplicableFrom())) {
			logger.debug("Price {} not applicable for time period {} (applicable from {})", price.getName(), timePeriod,
					price.getApplicableFrom());
			return null;
		}

		if (Boolean.TRUE.equals(price.getIsBundle()) && price.getPrices() != null) {
			RevenueItem bundleResult = this.getBundlePrice(price, timePeriod);
			return bundleResult;
		} else {
			RevenueItem priceRevenueItem = this.getAtomicPrice(price, timePeriod);
			if (priceRevenueItem == null) {
				logger.debug("Price {} not applicable (atomic price is null), skipping item creation.",
						price.getName());
				return null;
			}

			if (price.getDiscount() != null) {
				List<RevenueItem> discountRevenueItems = this.getDiscountItems(price, timePeriod);
				if (discountRevenueItems != null) {
					for (RevenueItem di : discountRevenueItems)
						priceRevenueItem.addRevenueItem(di);
				}
				/*
				 * if (!discountItems.isEmpty()) { if (item.getItems() == null) {
				 * item.setItems(new ArrayList<>()); } item.getItems().addAll(discountItems); }
				 */
			}

			return priceRevenueItem;
		}
	}

	private RevenueItem getBundlePrice(Price price, TimePeriod timePeriod) {
		logger.debug("Processing bundle price with operation: {}", price.getBundleOp());
		RevenueItem bundledRevenueItem;

		switch (price.getBundleOp()) {
		case CUMULATIVE:
			bundledRevenueItem = this.getCumulativePrice(price, timePeriod);
			break;
		case ALTERNATIVE_HIGHER:
			bundledRevenueItem = this.getHigherPrice(price, timePeriod);
			break;
		case ALTERNATIVE_LOWER:
			bundledRevenueItem = this.getLowerPrice(price, timePeriod);
			break;
		default:
			throw new IllegalArgumentException("Unknown bundle operation: " + price.getBundleOp());
		}

		if (bundledRevenueItem != null)
			bundledRevenueItem.setChargeTime(this.getChargeTime(timePeriod, price));

		return bundledRevenueItem;

	}

	private List<RevenueItem> getDiscountItems(Price price, TimePeriod timePeriod) {
		List<RevenueItem> discountItems = new ArrayList<>();

		Double amount = price.getAmount(); // Assuming amount is the base amount for the discount
		DiscountCalculator discountCalculator = new DiscountCalculator(subscription, metricsRetriever);
		RevenueItem discountItem = discountCalculator.compute(price.getDiscount(), timePeriod, amount);
		if (discountItem != null) {
			discountItems.add(discountItem);
		}
		return discountItems;
	}

	private RevenueItem getAtomicPrice(Price price, TimePeriod timePeriod) {
		logger.debug("Computing atomic price for: {}", price.getName());

		TimePeriod tp = getTimePeriod(price, timePeriod.getStartDateTime().plusSeconds(1));

		if (tp == null || !tp.getStartDateTime().equals(timePeriod.getStartDateTime())
				|| !tp.getEndDateTime().equals(timePeriod.getEndDateTime())) {
			return null;
		}

		String buyerId = subscription.getBuyerId();
		Double amountValue = this.computePrice(price, buyerId, timePeriod);

		if (amountValue == null || amountValue == 0.0) {
			logger.debug("Atomic price for {} is null or zero, returning null", price.getName());
			return null;
		}

		RevenueItem outItem = new RevenueItem(price.getName(), amountValue, "EUR");
		outItem.setChargeTime(this.getChargeTime(tp, price));

		// variable prices for future items lead to estimaed revenueItems
		if (price.isVariable() && outItem.getChargeTime().isAfter(OffsetDateTime.now())) {
			outItem.setEstimated(true);
		} else {
			outItem.setEstimated(false);
		}

		return outItem;
	}

	private OffsetDateTime getChargeTime(TimePeriod timePeriod, Price price) {
		if (price.getType() == null) {
//            logger.warn("Missing price type for price: {}. Defaulting to endTime", price.getName());
			return null;
//            return timePeriod.getEndDateTime();
		}
		switch (price.getType()) {
		case RECURRING_PREPAID:
		case ONE_TIME_PREPAID:
			return timePeriod.getStartDateTime();
		case RECURRING_POSTPAID:
			return timePeriod.getEndDateTime().minusSeconds(1);
		default:
//                logger.warn("Unknown price type for charge time: {}. Defaulting to endTime", price.getType());
//                return timePeriod.getEndDateTime();
			return null;
		}
	}

	private TimePeriod getTimePeriod(Price price, OffsetDateTime time) {
		if (price == null) {
			logger.error("Price is null, cannot determine time period");
			return null;
		}

		SubscriptionTimeHelper sth = new SubscriptionTimeHelper(subscription);
		TimePeriod tp = new TimePeriod();
		if (price.getType() != null) {
			switch (price.getType()) {
			case RECURRING_PREPAID:
				logger.debug("Computing charge period for RECURRING_PREPAID price type");
				tp = sth.getChargePeriodAt(time, price);
				break;
			case RECURRING_POSTPAID:
				logger.debug("Computing charge period for RECURRING_POSTPAID price type");
				tp = sth.getChargePeriodAt(time, price);
				break;
			case ONE_TIME_PREPAID:
				TimePeriod currentPeriod = sth.getSubscriptionPeriodAt(time);
				OffsetDateTime startDate = subscription.getStartDate();
				if (currentPeriod.getStartDateTime().equals(startDate)) {
					tp = currentPeriod;
				} else {
					logger.debug("Current period not match with startDate. It has probably already been calculated");
				}
				break;
			default:
				throw new IllegalArgumentException("Unsupported price type: " + price.getType());
			}
		} else {
			// TODO: GET PARENT TYPE FROM PRICE ?? - discuss if it can be removed
//            tp = sth.getSubscriptionPeriodAt(time);
			tp = this.getTimePeriod(price.getParentPrice(), time);
		}

		logger.debug("Computed time period for price {} - tp: {} - {}", price.getName(), tp.getStartDateTime(),
				tp.getEndDateTime());

		return tp;
	}

	private Double computePrice(Price price, String buyerId, TimePeriod tp) {
		Double applicableValue = getApplicableValue(price, buyerId, tp);

		if (applicableValue == null) {
			// if not exists an applicable or an computation then we had only amount price
			return price.getAmount();
		}

		// if value in range then computation
		if (price.getApplicableBaseRange().inRange(applicableValue)) {
			logger.info("Applicable value: {}, for price: {}, in tp: {} - {}", applicableValue, price.getName(), tp.getStartDateTime(),tp.getEndDateTime());

			return getComputationValue(price, buyerId, tp);
		} else {
			return null;
		}
	}

	private Double getComputationValue(Price price, String buyerId, TimePeriod tp) {
		if (price.getComputationBase() == null || price.getComputationBase().isEmpty()) {
			logger.debug("No computation base defined!");
			return null;
		}

		if (price.getPercent() == null && price.getAmount() == null) {
			logger.debug("Neither percent nor amount defined for computation!");
			return null;
		}

		try {
			TimePeriod calculationPeriod = tp;
			String referencePeriod = price.getComputationBaseReferencePeriod() != null
					? price.getComputationBaseReferencePeriod().getValue()
					: null;

			if (referencePeriod != null) {
				SubscriptionTimeHelper helper = new SubscriptionTimeHelper(subscription);

				if ("PREVIOUS_SUBSCRIPTION_PERIOD".equals(referencePeriod)) {
					calculationPeriod = helper.getPreviousSubscriptionPeriod(tp.getEndDateTime());
				} else if ((referencePeriod.startsWith("PREVIOUS_") || referencePeriod.startsWith("LAST_"))
						&& referencePeriod.endsWith("_CHARGE_PERIODS")) {
					calculationPeriod = helper.getCustomPeriod(tp.getEndDateTime(), price, referencePeriod);

					if (calculationPeriod == null) {
						logger.debug("Could not compute custom period for reference: {}", referencePeriod);
						return null;
					}

					logger.debug("Using custom period for {}: {} - {}, based on reference: {}", referencePeriod,
							calculationPeriod.getStartDateTime(), calculationPeriod.getEndDateTime(), referencePeriod);
				}
			}

			Double computationValue = metricsRetriever.computeValueForKey(price.getComputationBase(), buyerId,
					calculationPeriod);

			logger.info("Computation value computed: {} for base '{}' in period: {} - {}, based on reference: {}",
					computationValue, price.getComputationBase(), calculationPeriod.getStartDateTime(),
					calculationPeriod.getEndDateTime(), referencePeriod);

			if (price.getPercent() != null) {
				return computationValue * (price.getPercent() / 100);
			} else {
				return price.getAmount();
			}

		} catch (Exception e) {
			logger.error("Error computing value for base '{}': {}", price.getComputationBase(), e.getMessage(), e);
			return null;
		}
	}

	private Double getApplicableValue(Price price, String buyerId, TimePeriod tp) {
		if (price.getApplicableBase() == null || price.getApplicableBase().isEmpty()) {
			return null;
		}

		try {
			TimePeriod actualTp = tp;
			String referencePeriod = price.getApplicableBaseReferencePeriod().getValue();

			if (referencePeriod != null) {
				SubscriptionTimeHelper helper = new SubscriptionTimeHelper(subscription);

				if ("PREVIOUS_SUBSCRIPTION_PERIOD".equals(referencePeriod)) {
					actualTp = helper.getPreviousSubscriptionPeriod(tp.getEndDateTime());
				} else if ((referencePeriod.startsWith("PREVIOUS_") || referencePeriod.startsWith("LAST_"))
						&& referencePeriod.endsWith("_CHARGE_PERIODS")) {
					actualTp = helper.getCustomPeriod(tp.getEndDateTime(), price, referencePeriod);

					if (actualTp == null) {
						logger.debug("Could not compute custom period for reference: {}", referencePeriod);
						return null;
					}

					logger.debug("Using custom period for {}: {} - {}, based on reference: {}", referencePeriod,
							actualTp.getStartDateTime(), actualTp.getEndDateTime(), referencePeriod);
				}
			}

			Double applicableValue = metricsRetriever.computeValueForKey(price.getApplicableBase(), buyerId, actualTp);

			return applicableValue;
		} catch (Exception e) {
			logger.error("Error computing applicable value for base '{}': {}", price.getApplicableBase(),
					e.getMessage(), e);
			return null;
		}
	}

	private RevenueItem getCumulativePrice(Price bundlePrice, TimePeriod timePeriod) {
		List<Price> childPrices = bundlePrice.getPrices();
		logger.debug("Computing cumulative price from {} items", childPrices.size());

		RevenueItem cumulativeItem = new RevenueItem(bundlePrice.getName(), bundlePrice.getCurrency());
		cumulativeItem.setItems(new ArrayList<>());

		for (Price p : childPrices) {
			RevenueItem childRevenueItem = this.compute(p, timePeriod);
			if (childRevenueItem != null) {
//                cumulativeItem.getItems().add(current);
				cumulativeItem.addRevenueItem(childRevenueItem);
			}
		}

		if (cumulativeItem.getItems().isEmpty()) {
			return null;
		}

		return cumulativeItem;
	}

	private RevenueItem getHigherPrice(Price bundlePrice, TimePeriod timePeriod) {
		List<Price> childPrices = bundlePrice.getPrices();
		logger.debug("Finding higher price from {} items", childPrices.size());

		RevenueItem bestItem = null;

		for (Price p : childPrices) {
			RevenueItem current = this.compute(p, timePeriod);
			if (current == null)
				continue;

			if (bestItem == null || current.getOverallValue() > bestItem.getOverallValue()) {
				bestItem = current;
			}
		}

		if (bestItem == null) {
			return null;
		}

		RevenueItem wrapper = new RevenueItem(bundlePrice.getName(), bundlePrice.getCurrency());
		wrapper.addRevenueItem(bestItem);
		/*
		 * List<RevenueItem> items = new ArrayList<>(); items.add(bestItem);
		 * wrapper.setItems(items);
		 */

		return wrapper;
	}

	private RevenueItem getLowerPrice(Price bundlePrice, TimePeriod timePeriod) {
		List<Price> childPrices = bundlePrice.getPrices();
		logger.debug("Finding lower price from {} items", childPrices.size());

		RevenueItem bestItem = null;

		for (Price p : childPrices) {
			RevenueItem current = this.compute(p, timePeriod);
			if (current == null)
				continue;

			if (bestItem == null || current.getOverallValue() < bestItem.getOverallValue()) {
				bestItem = current;
			}
		}

		if (bestItem == null) {
			return null;
		}

		RevenueItem wrapper = new RevenueItem(bundlePrice.getName(), bundlePrice.getCurrency());
		wrapper.addRevenueItem(bestItem);
		/*
		 * List<RevenueItem> items = new ArrayList<>(); items.add(bestItem);
		 * wrapper.setItems(items);
		 */

		return wrapper;
	}
}
