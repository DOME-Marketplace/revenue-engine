package it.eng.dome.revenue.engine.service.compute2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class CumulativeCalculator extends AbstractCalculator {

    public CumulativeCalculator(Subscription subscription, PlanItem item) {
        super(subscription, item);
    }

    public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) {

		List<PlanItem> bundleItems = this.item.getBundleItems();

		RevenueItem cumulativeRevenueItem = new RevenueItem(this.item.getName(), this.item.getCurrency());		
		if(this.item.getType()!=null)
			cumulativeRevenueItem.setType(this.item.getType().toString());

		// first process prices
		for (PlanItem price : bundleItems) {
			if(!(price instanceof Price))
				continue;
			Calculator childCalculator = CalculatorFactory.getCalculatorFor(this.getSubscription(), price);
			RevenueItem childRevenueItem = childCalculator.compute(timePeriod, computeContext);
			if (childRevenueItem != null) {
				cumulativeRevenueItem.addRevenueItem(childRevenueItem);
			}
		}

		if(this.item instanceof Price) {
			Price price = (Price)this.item;
			if(price.getDiscount()!=null) {
				Map<String, Double> discountContext = new HashMap<>();
				discountContext.put("parent-price", cumulativeRevenueItem.getOverallValue());
				Calculator discountCalculator = CalculatorFactory.getCalculatorFor(this.getSubscription(), price.getDiscount());
				RevenueItem discountRevenueItem = discountCalculator.compute(timePeriod, discountContext);
				if(discountRevenueItem!=null) {
					cumulativeRevenueItem.addRevenueItem(discountRevenueItem);
				}
			}
		}

		if (cumulativeRevenueItem.getItems().isEmpty()) {
			return null;
		} else {
			return cumulativeRevenueItem;
		}
	}

}
