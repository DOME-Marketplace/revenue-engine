package it.eng.dome.revenue.engine.service.compute;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class AtomicDiscountCalculator extends AbstractCalculator {

	private static final Logger logger = LoggerFactory.getLogger(AtomicDiscountCalculator.class);

    public AtomicDiscountCalculator(Subscription sub, Discount discount) {
        super(sub, discount);
    }

    @Override
    public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) {
        logger.debug("Computing atomic discount for: {}", this.item.getName());

        // seller id is the subscriber, by default. But can be overridden using the calculator context
        String sellerId = this.getSubscription().getSubscriberId();
        if(this.getCalculatorContext()!=null && this.getCalculatorContext().get("sellerId")!=null)
            sellerId = this.getCalculatorContext().get("sellerId");

        Double discountValue = this.computeDiscountValue(sellerId, timePeriod, computeContext);

        if (discountValue == null) {
            logger.debug("Atomic discount for {} is null, returning null", this.item.getName());
            return null;
        }

        return new RevenueItem(this.item.getName(), -discountValue, "EUR");
    }

	private Double computeDiscountValue(String sellerId, TimePeriod tp, Map<String, Double> computeContext) {
		try {
			if (this.item.getPercent() != null) {

                Double computationBase;
                if ("parent-price".equals(this.item.getComputationBase()) && computeContext.containsKey("parent-price")) {
                    // TODO: make this more generic to look for any key in the map first; and only after ask the metrics retriever.
                    computationBase = computeContext.get("parent-price");
                    logger.debug("Using parent price amount as computation base: {}", computationBase);
                } else {
                    TimePeriod computationPeriod = this.getComputationTimePeriod(tp.getEndDateTime());
                    if (computationPeriod == null) {
                        logger.debug("Could not compute custom period for reference: {}", this.item.getComputationBaseReferencePeriod());
                        return null;
                    }
                    logger.debug("Using custom period for {}: {} - {}, based on reference: {}", this.item.getComputationBaseReferencePeriod(), computationPeriod.getStartDateTime(), computationPeriod.getEndDateTime());
                    computationBase = this.metricsRetriever.computeValueForKey(this.item.getComputationBase(), sellerId, computationPeriod);
                    logger.info("Computation base computed as: {} in tp: {}", computationBase, computationPeriod);
                }

				if(computationBase==null) {
					logger.debug("Computation base is null. Returning.");
					return null;
				}

				return computationBase * (this.item.getPercent() / 100);
			} 
			else {
				return this.item.getAmount();
			}
		} catch (Exception e) {
			logger.error("Error computing value for base '{}': {}", this.item.getComputationBase(), e.getMessage(), e);
			return null;
		}
    }

}
