package it.eng.dome.revenue.engine.service.compute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class AlternativeCalculator extends AbstractCalculator {

    private boolean higher;

    public AlternativeCalculator(Subscription sub, PlanItem bundle, Boolean higher) {
        super(sub, bundle);
        this.higher = higher;        
    }

    public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) throws BadTmfDataException, ExternalServiceException {
		List<PlanItem> childItems = this.item.getBundleItems();
		RevenueItem selectedItem = null;

		// compute the computationbase, so that the context contains it
        String sellerId = this.getSubscription().getSubscriberId();
        if(this.getCalculatorContext()!=null && this.getCalculatorContext().get("sellerId")!=null)
            sellerId = this.getCalculatorContext().get("sellerId");
		this.getComputationBase(sellerId, timePeriod, computeContext);

		for (PlanItem item : childItems) {
			Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), item, this);
			RevenueItem current = childCalc.compute(timePeriod, computeContext);
			if (current == null)
				continue;

			if (selectedItem == null 
                    || (higher && current.getOverallValue() > selectedItem.getOverallValue()) 
                    || (!higher && current.getOverallValue() < selectedItem.getOverallValue())) {
				selectedItem = current;
			}
		}
		if (selectedItem == null) {
			return null;
		}
		RevenueItem wrapper = new RevenueItem(item.getName(), item.getCurrency());
		wrapper.addRevenueItem(selectedItem);

		// FIXME: what if the alternative bundle is a price? It might also have a Discount which needs to be included
		// Quick and dirty workaround below
		if(this.item instanceof Price) {
			Price p = (Price)this.item;
			if(p.getDiscount()!=null) {
				Map<String, Double> discountContext = new HashMap<>();
				discountContext.put("parent-price", selectedItem.getOverallValue());
				Calculator discountCalculator = CalculatorFactory.getCalculatorFor(this.getSubscription(), p.getDiscount(), this);
				RevenueItem discountRevenueItem = discountCalculator.compute(timePeriod, discountContext);
				if(discountRevenueItem!=null) {
					wrapper.addRevenueItem(discountRevenueItem);
				}
			}
		}

		if (wrapper.getItems().isEmpty()) {
			return null;
		}

		return wrapper;
	}


}
