package it.eng.dome.revenue.engine.service.compute;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
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
    public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) throws ExternalServiceException, BadTmfDataException {
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

	private Double computeDiscountValue(String sellerId, TimePeriod tp, Map<String, Double> computeContext) throws ExternalServiceException, BadTmfDataException {
		if (this.item.getPercent() != null) {
			return this.getComputationBase(sellerId, tp, computeContext) * (this.item.getPercent() / 100);
		}
		else if (this.item.getUnitAmount() != null) {
			return this.getComputationBase(sellerId, tp, computeContext) * this.item.getUnitAmount();
		}
		else {
			return this.item.getAmount();
		}
    }



}
