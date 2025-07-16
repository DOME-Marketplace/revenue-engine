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
		RevenueItem item = new RevenueItem();
		item.setName(price.getName());
		// check if is bundle
		if (Boolean.TRUE.equals(price.getIsBundle()) && price.getPrices() != null) {
			RevenueItem bundleResult = getBundlePrice(price, time);
			item.setItems(bundleResult.getItems());
		} else {
			if( getAtomicPrice(price, time) != null) {;
				item = getAtomicPrice(price, time);
			}else {
				//rifare giro logica - capire se qua si puo uscire o [devo continuare (!!!)]
			}
		}
		
		logger.info("Item computed: {}", item.toString());

		if (price.getDiscount() != null) {
			List<RevenueItem> discountItems = getDiscountItems(price, time);

			if (!discountItems.isEmpty()) {
				if (item.getItems() == null) {
					item.setItems(new ArrayList<>());
				}
				item.getItems().addAll(discountItems);
			}
		}
		return item;
	}
	
	private RevenueItem getBundlePrice(Price price, OffsetDateTime time) {
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

		return bundleResult;
	}
	
	private List<RevenueItem> getDiscountItems(Price price, OffsetDateTime time){
		List<RevenueItem> discountItems = new ArrayList<>();

		Double amount = price.getAmount(); // TODO implement fixed fee logic
		DiscountCalculator discountCalculator = new DiscountCalculator(subscription, metricsRetriever);
		RevenueItem discountItem = discountCalculator.compute(price.getDiscount(), time, amount);
		if (discountItem != null && discountItem.getValue() != 0) {
			discountItems.add(discountItem);
		}
		return discountItems;
	}
	
	private RevenueItem getAtomicPrice(Price price, OffsetDateTime time) {
		logger.debug("Computing atomic price for: {}", price.getName());
		//retrieve period
		TimePeriod tp = getTimePeriod(price, time);
		
		String buyerId = subscription.getBuyerId();
        Double amountValue = computePrice(price, buyerId, tp);
        
        if(amountValue == null) {
        	return null;
        }

        return new RevenueItem(price.getName(), amountValue, "EUR");
	}

	private TimePeriod getTimePeriod(Price price, OffsetDateTime time) {
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
					//TODO: manage this case
					logger.warn("current period not match with startDate");
				}
				break;
			default:
				throw new IllegalArgumentException("Unsupported price type: " + price.getType());
			}
		} else {
			// TODO: GET PARENT TYPE FROM PRICE
			tp = sth.getSubscriptionPeriodAt(time);
		}
		
		return tp;
	}
	
	private Double computePrice (Price price, String buyerId, TimePeriod tp) {
		Double applicableValue = getApplicableValue(price, buyerId, tp);
		
		logger.info("applicable value computed: {}", applicableValue);
		
		if(applicableValue == null) {
			// if not exists an applicable or an computation then we had only amount price
			return price.getAmount();
		}
		
		// if value in range then computation 
		if(price.getApplicableBaseRange().inRange(applicableValue)) {
			 return getComputationValue(price, buyerId, tp);
		} else {
        	// when not in range
			logger.info("Not in range {}", applicableValue);
        	return null;
        }
    }
	
	private Double getComputationValue(Price price, String buyerId, TimePeriod tp) {
		logger.info("Comnputation of value");
		// computation logic
		if(price.getComputationBase() != null && !price.getComputationBase().isEmpty()) {
			// TODO: consider computationBaseReferencePeriod and computationFrom - work on tp
			Double computationValue = 0.0;
			try {
				computationValue = metricsRetriever.computeValueForKey(price.getComputationBase(), buyerId, tp);
				computationValue = 260000.00;
				logger.info("VComputed value: {}", computationValue);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		    if (price.getPercent() != null) {
		    	return (computationValue * (price.getPercent() / 100));
		    } else if (price.getAmount() != null) {
		    	return price.getAmount();
		    	// TODO: discutere di come gestire questo amount o percent e sicuro questa non è la posizione per fare questo if
		    }
		    
		} else {
			//TODO:  logic when computation not exits
			logger.info("computation not exists");
			return 0.0;
		}
		
		return null;
	}
	
	private Double getApplicableValue(Price price, String buyerId, TimePeriod tp) {
		Double applicableValue = 0.0;

        //APPLICABLE LOGIC
        if (price.getApplicableBase() != null && !price.getApplicableBase().isEmpty()) {
        	// TODO: consider applicableBaseReferencePeriod and applicableFrom - work on tp
        	try {
				applicableValue = metricsRetriever.computeValueForKey(price.getApplicableBase(), buyerId, tp);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	applicableValue += 200000.00; // Simulating a base value for testing purposes
        } else {
        	return null;
        }
        
        return applicableValue;
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
