package it.eng.dome.revenue.engine.service.compute2;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class ForEachCalculator extends AbstractCalculator {

    private static final Logger logger = LoggerFactory.getLogger(ForEachCalculator.class);

    public ForEachCalculator(Subscription subscription, PlanItem bundle) {
        super(subscription, bundle);
    }

    public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) {
		// prepare the output
		RevenueItem outputItem = new RevenueItem(this.item.getName(), this.item.getCurrency());

		// extract the foreach property
		String iterateOver = this.item.getForEachMetric();
		if(iterateOver==null || iterateOver.isBlank()) {
			logger.error("Found a 'foreach' price bundle without a 'forEachMetric' property. Skipping the whole bundle price");
			return null;
		}
		// retrieve the possible values
		List<String> sellerIds = this.metricsRetriever.getDistinctValuesForKey(iterateOver, this.getSubscription().getSubscriberId(), timePeriod);

		// foreach 'iterator' property, build a sub-revenueItem with all child prices computed with the 'iterator' property.
		for(String sellerId: sellerIds) {
			RevenueItem sellerRevenueItem = new RevenueItem(this.item.getName() + " for seller " + sellerId, this.item.getCurrency());
			if(this.item.getType()!=null)
				sellerRevenueItem.setType(this.item.getType().toString());
			for (PlanItem childItem : this.item.getBundleItems()) {
				// Below, we need to pass a map o properties to be used as a filter
				// normal...... look for 'seller' (which is in the subscription)
				// federated... look for 'referenceMarketplace' and iterate over 'seller'
				Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), childItem);
				RevenueItem childRevenueItem = childCalc.compute(timePeriod, computeContext);
				if (childRevenueItem != null) {
					sellerRevenueItem.addRevenueItem(childRevenueItem);
				}
			}
			outputItem.addRevenueItem(sellerRevenueItem);
		}

		return outputItem;
	}
}
