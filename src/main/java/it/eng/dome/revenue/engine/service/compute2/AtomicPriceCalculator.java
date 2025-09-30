package it.eng.dome.revenue.engine.service.compute2;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) {
	    logger.debug("Computing price item: {}", this.item.getName());
		
		// first compute the atomic price
		RevenueItem outRevenueItem = this.computeAtomicPrice(timePeriod);

		if (outRevenueItem == null){
			outRevenueItem = new RevenueItem(this.item.getName(), 0.0, this.item.getCurrency());
		}			
		logger.info("**********************Computed atomic price item: {} with value: {}", outRevenueItem.getName(), outRevenueItem.getValue());

		// prepare the map
		Map<String, Double> context4discount = new HashMap<>();
		context4discount.put("parent-price", outRevenueItem.getOverallValue());

		// then, add discount if any
		if (((Price)this.item).getDiscount() != null) {
			RevenueItem discountItem = this.computeDiscountRevenueItem(timePeriod, context4discount);
			if(discountItem!=null) {
				outRevenueItem.addRevenueItem(discountItem);
				logger.info("**********************Added discount item: {} with value: {}. Updated overall value: {}", discountItem.getName(), discountItem.getValue(), outRevenueItem.getOverallValue());
			}
		}

		return outRevenueItem;
	}

	/**
	 * Computes discount items for a price
	 * @param price the Price object containing discount information
	 * @param timePeriod the TimePeriod for computation
	 * @return List of RevenueItems representing discounts
	 */
	private RevenueItem computeDiscountRevenueItem(TimePeriod timePeriod, Map<String, Double> computationContext) {
		
		Calculator dc = CalculatorFactory.getCalculatorFor(this.getSubscription(), ((Price)this.item).getDiscount());
		RevenueItem discountItem = dc.compute(timePeriod, computationContext);

		/*
		Double amount = price.getAmount(); // Assuming amount is the base amount for the discount
		discountCalculator.setSubscription(subscription);
		RevenueItem discountItem = discountCalculator.compute(price.getDiscount(), timePeriod, amount);
		if (discountItem != null) {
			discountItems.add(discountItem);
		}
		*/
		return discountItem;
	}

    private RevenueItem computeAtomicPrice(TimePeriod timePeriod) {

		logger.debug("Computing atomic price for: {}", this.item.getName());

		String subscriberId = this.getSubscription().getSubscriberId();
		// the subscriber can be either a Seller or a ReferenceMarketplace)
		Double amountValue = this.computePriceValue(subscriberId, timePeriod);

		if (amountValue == null || amountValue == 0.0) {
			logger.debug("Atomic price for {} is null or zero, returning null", this.item.getName());
			return null;
		}

		RevenueItem outItem = new RevenueItem(this.item.getName(), amountValue, "EUR");
		outItem.setChargeTime(new SubscriptionTimeHelper(this.getSubscription()).getChargeTime(timePeriod, this.item.getReferencePrice()));
		if(this.item.getType()!=null)
			outItem.setType(this.item.getType().toString());

		return outItem;
	}


	private Double computePriceValue(String subscriberId, TimePeriod tp) {

		// FIXME: if being not applicable means skipping the item, then move this to abstractCalculator.compute
		if(!this.checkApplicability(tp))
			return null;

		if(!this.checkComputability(tp))
			return null;

		try {
			TimePeriod calculationPeriod = tp;
			String referencePeriod = this.item.getComputationBaseReferencePeriod() != null
					? this.item.getComputationBaseReferencePeriod().getValue()
					: null;

			if (referencePeriod != null) {
				SubscriptionTimeHelper helper = new SubscriptionTimeHelper(this.getSubscription());

				if ("PREVIOUS_SUBSCRIPTION_PERIOD".equals(referencePeriod)) {
					calculationPeriod = helper.getPreviousSubscriptionPeriod(tp.getEndDateTime());
				} else if ((referencePeriod.startsWith("PREVIOUS_") || referencePeriod.startsWith("LAST_"))
						&& referencePeriod.endsWith("_CHARGE_PERIODS")) {
					calculationPeriod = helper.getCustomPeriod(tp.getEndDateTime(), this.item.getReferencePrice(), referencePeriod);

					if (calculationPeriod == null) {
						logger.debug("Could not compute custom period for reference: {}", referencePeriod);
						return null;
					}

					logger.debug("Using custom period for {}: {} - {}, based on reference: {}", referencePeriod,
							calculationPeriod.getStartDateTime(), calculationPeriod.getEndDateTime(), referencePeriod);
				}
			}

			Double computationValue = metricsRetriever.computeValueForKey(this.item.getComputationBase(), subscriberId,
					calculationPeriod);

			logger.info("Computation value computed: {} for base '{}' in period: {} - {}, based on reference: {}",
					computationValue, this.item.getComputationBase(), calculationPeriod.getStartDateTime(),
					calculationPeriod.getEndDateTime(), referencePeriod);

			if (this.item.getPercent() != null) {
				return computationValue * (this.item.getPercent() / 100);
			} else {
				return this.item.getAmount();
			}

		} catch (Exception e) {
			logger.error("Error computing value for base '{}': {}", this.item.getComputationBase(), e.getMessage(), e);
			return null;
		}
	}

}
