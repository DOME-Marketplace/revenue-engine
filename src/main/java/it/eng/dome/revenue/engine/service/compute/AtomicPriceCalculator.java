package it.eng.dome.revenue.engine.service.compute;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class AtomicPriceCalculator extends AbstractCalculator {

	private static final Logger logger = LoggerFactory.getLogger(AtomicPriceCalculator.class);

    public AtomicPriceCalculator(Subscription sub, Price price) {
        super(sub, price);
    }

	public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) throws ExternalServiceException, BadTmfDataException {
	    logger.debug("Computing price item: {}", this.item.getName());
		
		// first compute the atomic price
		RevenueItem outRevenueItem = this.computeAtomicPrice(timePeriod);
		if (outRevenueItem == null){
			logger.debug("Revenue Item for {} is null in period {}. Returning", this.item, timePeriod);
			return null;
		}

		logger.info("Computed atomic price item: {} with value: {}", outRevenueItem.getName(), outRevenueItem.getValue());

		// prepare the map
		Map<String, Double> context4discount = new HashMap<>();
		context4discount.put("parent-price", outRevenueItem.getOverallValue());

		// then, add discount if any
		if (((Price)this.item).getDiscount() != null) {
			RevenueItem discountItem = this.computeDiscountRevenueItem(timePeriod, context4discount);
			if(discountItem!=null) {
				outRevenueItem.addRevenueItem(discountItem);
				logger.info("Added discount item: {} with value: {}. Updated overall value: {}", discountItem.getName(), discountItem.getValue(), outRevenueItem.getOverallValue());
			}
		}

		return outRevenueItem;
	}

	/**
	 * Computes discount items for a price
	 * @param timePeriod the TimePeriod for computation
	 * @return List of RevenueItems representing discounts
	 */
	private RevenueItem computeDiscountRevenueItem(TimePeriod timePeriod, Map<String, Double> computationContext) throws BadTmfDataException, ExternalServiceException {
		Calculator dc = CalculatorFactory.getCalculatorFor(this.getSubscription(), ((Price)this.item).getDiscount(), this);
        return dc.compute(timePeriod, computationContext); //discountItem
	}

    private RevenueItem computeAtomicPrice(TimePeriod timePeriod) throws ExternalServiceException, BadTmfDataException {

		logger.debug("Computing atomic price for '{}' for time period {}", this.item.getName(), timePeriod);

        // seller id is the subscriber, by default. But can be overridden using the calculator context
        String sellerId = this.getSubscription().getSubscriberId();
        if(this.getCalculatorContext()!=null && this.getCalculatorContext().get("sellerId")!=null) {
            sellerId = this.getCalculatorContext().get("sellerId");
		}

		Double priceValue = this.computePriceValue(sellerId, timePeriod);

		if (priceValue == null) {
			logger.debug("Atomic price for {} is null or zero, returning null", this.item.getName());
			return null;
		}

		RevenueItem outItem = new RevenueItem(this.item.getName(), priceValue, "EUR");
		outItem.setChargeTime(new SubscriptionTimeHelper(this.getSubscription()).getChargeTime(timePeriod, this.item.getReferencePrice()));
		if(this.item.getType()!=null)
			outItem.setType(this.item.getType().toString());

		return outItem;
	}

	private Double computePriceValue(String sellerId, TimePeriod tp) throws ExternalServiceException, BadTmfDataException {
		if (this.item.getPercent() != null) {
			return this.getComputationBase(sellerId, tp, null) * (this.item.getPercent() / 100);
		}
		else if (this.item.getUnitAmount() != null) {
			return this.getComputationBase(sellerId, tp, null) * this.item.getUnitAmount();
		}
		else {
			return this.item.getAmount();
		}
	}

}
