package it.eng.dome.revenue.engine.service.compute2;

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

		List<PlanItem> childItems = this.item.getBundleItems();

		RevenueItem cumulativeItem = new RevenueItem(this.item.getName(), this.item.getCurrency());
		if(this.item.getType()!=null)
			cumulativeItem.setType(this.item.getType().toString());


		for (PlanItem p : childItems) {
			Double parentPrice = 0.0;
			Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), p);
			RevenueItem childRevenueItem = childCalc.compute(timePeriod, computeContext);
			if (childRevenueItem != null) {
				parentPrice+=childRevenueItem.getOverallValue();
				cumulativeItem.addRevenueItem(childRevenueItem);
			}
		}

		// computeContext.put("parentPrice", parentPrice);

		// compute discount

		// now compute the discount, if any
		// FIXME: how to manage discounts at this stage? Item can be either a price or a discount. No getDiscount here.

		if (cumulativeItem.getItems().isEmpty()) {
			return null;
		}

		return cumulativeItem;
	}

}
