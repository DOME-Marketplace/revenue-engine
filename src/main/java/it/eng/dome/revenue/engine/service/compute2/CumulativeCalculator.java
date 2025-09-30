package it.eng.dome.revenue.engine.service.compute2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) {

		List<PlanItem> bundleItems = this.item.getBundleItems();

		RevenueItem cumulativeItem = new RevenueItem(this.item.getName(), this.item.getCurrency());		
		if(this.item.getType()!=null)
			cumulativeItem.setType(this.item.getType().toString());

		// first process prices
		for (PlanItem p : bundleItems) {
			if(!(p instanceof Price))
				continue;
			Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), p);
			RevenueItem childRevenueItem = childCalc.compute(timePeriod, computeContext);
			if (childRevenueItem != null) {
				cumulativeItem.addRevenueItem(childRevenueItem);
			}
		}

		// FIXME: what if the cumulative bundle is a price? It might also have a Discount which needs to be included
		// Quick and dirty workaround below
		if(this.item instanceof Price) {
			Price p = (Price)this.item;
			if(p.getDiscount()!=null) {
				Map<String, Double> discountContext = new HashMap<>();
				discountContext.put("parent-price", cumulativeItem.getOverallValue());
				Calculator discountCalculator = CalculatorFactory.getCalculatorFor(this.getSubscription(), p.getDiscount());
				RevenueItem discountRevenueItem = discountCalculator.compute(timePeriod, discountContext);
				if(discountRevenueItem!=null) {
					cumulativeItem.addRevenueItem(discountRevenueItem);
				}
			}
		}

		if (cumulativeItem.getItems().isEmpty()) {
			return null;
		}

		return cumulativeItem;
	}

}
