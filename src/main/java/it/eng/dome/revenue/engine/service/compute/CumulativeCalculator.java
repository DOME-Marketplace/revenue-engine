package it.eng.dome.revenue.engine.service.compute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class CumulativeCalculator extends AbstractCalculator {

    public CumulativeCalculator(Subscription subscription, PlanItem item) {
        super(subscription, item);
    }

    public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) throws BadTmfDataException, ExternalServiceException {

		List<PlanItem> bundleItems = this.item.getBundleItems();

		RevenueItem cumulativeRevenueItem = new RevenueItem(this.item.getName(), this.item.getCurrency());		
		if(this.item.getType()!=null)
			cumulativeRevenueItem.setType(this.item.getType().toString());

		// compute the computationbase, so that the context contains it
        String sellerId = this.getSubscription().getSubscriberId();
        if(this.getCalculatorContext()!=null && this.getCalculatorContext().get("sellerId")!=null)
            sellerId = this.getCalculatorContext().get("sellerId");
		this.getComputationBase(sellerId, timePeriod, computeContext);

		// first process child prices
		for (PlanItem price : bundleItems) {
			if(!(price instanceof Price))
				continue;
			Calculator childCalculator = CalculatorFactory.getCalculatorFor(this.getSubscription(), price, this);
			RevenueItem childRevenueItem = childCalculator.compute(timePeriod, computeContext);
			if (childRevenueItem != null) {
				cumulativeRevenueItem.addRevenueItem(childRevenueItem);
			}
		}

		// then process the item itself (either a cumulative price...
		if(this.item instanceof Price) {
			Price price = (Price)this.item;
			if(price.getDiscount()!=null) {
				Map<String, Double> discountContext = new HashMap<>();
				discountContext.put("parent-price", cumulativeRevenueItem.getOverallValue());
				Calculator discountCalculator = CalculatorFactory.getCalculatorFor(this.getSubscription(), price.getDiscount(), this);
				RevenueItem discountRevenueItem = discountCalculator.compute(timePeriod, discountContext);
				if(discountRevenueItem!=null) {
					cumulativeRevenueItem.addRevenueItem(discountRevenueItem);
				}
			}
		}
		
		// ... or a cumulative discount.
	    if (this.item instanceof Discount) {
	        for (PlanItem subItem : this.item.getBundleItems()) {
                Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), subItem, this);
                RevenueItem childRev = childCalc.compute(timePeriod, computeContext);
                if (childRev != null)
                    cumulativeRevenueItem.addRevenueItem(childRev);
	        }
	    }

		// if (cumulativeRevenueItem.getItems().isEmpty()) {
		// 	return null;
		// } else {
		// 	return cumulativeRevenueItem;
		// }

		return cumulativeRevenueItem;

	}

}
