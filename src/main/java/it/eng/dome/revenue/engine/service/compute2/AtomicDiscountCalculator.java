package it.eng.dome.revenue.engine.service.compute2;

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

		logger.debug("Computing atomic price for: {}", this.item.getName());

		RevenueItem outItem = this.computeAtomicDiscount(timePeriod, computeContext);

		return outItem;
	}

    private RevenueItem computeAtomicDiscount(TimePeriod timePeriod, Map<String, Double> computeContext) {
        logger.debug("Computing atomic discount for: {}", this.item.getName());

        String subscriberId = this.getSubscription().getSubscriberId();
        Double amountValue = this.computeDiscountValue(subscriberId, timePeriod, computeContext);

        if (amountValue == null || amountValue == 0.0) {
            logger.debug("Atomic discount for {} is null, returning null", this.item.getName());
            return null;
        }

        return new RevenueItem(this.item.getName(), -amountValue, "EUR");
    }




	private Double computeDiscountValue(String subscriberId, TimePeriod tp, Map<String, Double> computeContext) {

		// FIXME: if being not applicable means skipping the item, then move this to abstractCalculator.compute (or in the various skip checks)
		if(!this.checkApplicability(tp))
			return null;

		if(!this.checkComputability(tp))
			return null;

		// TODO: maybe some checks below are already covered above
		logger.debug("Computation of discount value");
        if (this.item.getComputationBase() != null && !this.item.getComputationBase().isEmpty()) {
            if (this.item.getPercent() != null) {
                Double computationValue = 0.0;
                try {
                    if ("parent-price".equals(this.item.getComputationBase()) && computeContext.containsKey("parent-price")) {
                        // TODO: make this more generic to look for any key in the map first; and only after ask the metrics retriever.
                        computationValue = computeContext.get("parent-price");
                        logger.debug("Using parent price amount: {}", computationValue);
                    } else {
                        computationValue = metricsRetriever.computeValueForKey(this.item.getComputationBase(), subscriberId, tp);
                        logger.info("Computation value computed: {} in tp: {}", computationValue, tp);
                    }
                } catch (Exception e) {
                    logger.error("Error computing discount value: {}", e.getMessage(), e);
                }

                return (computationValue * (this.item.getPercent() / 100));
            } else if (this.item.getAmount() != null) {
                return this.item.getAmount();
            }
        } else {
            // TODO: discuss about this else
            logger.warn("Computation not exists!");
        }
        return null;
    }

}
