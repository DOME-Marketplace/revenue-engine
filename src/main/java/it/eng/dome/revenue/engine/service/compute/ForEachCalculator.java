package it.eng.dome.revenue.engine.service.compute;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
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

		// only 'activeMarketplaceSeller' is currently supported
		if(!"activeMarketplaceSeller".equalsIgnoreCase(iterateOver)) {
			logger.error("{} is not supported. Currently, only 'marketplaceSeller' metric is supported.");
			return null;
		}

		// now process them
		if("activeMarketplaceSeller".equalsIgnoreCase(iterateOver)) {
			// retrieve the possible values
			try {
				List<String> sellerIds = this.metricsRetriever.getDistinctValuesForKey(iterateOver, this.getSubscription().getSubscriberId(), timePeriod);

				logger.debug("Found {} sellers behind marketplace {} in period {}", sellerIds.size(), this.getSubscription().getSubscriberId(), timePeriod);

				// foreach 'iterator' property, build a sub-revenueItem with all child prices computed with the 'iterator' property.
				for(String sellerId: sellerIds) {

					String orgLabel = this.getOrganisationLabel(sellerId);

					logger.debug("looking for transactions of seller {} in period {}", orgLabel, timePeriod);
					RevenueItem sellerRevenueItem = new RevenueItem(this.item.getName() + " - Share from '" + orgLabel + "'", this.item.getCurrency());
					if(this.item.getType()!=null)
						sellerRevenueItem.setType(this.item.getType().toString());
					for (PlanItem childItem : this.item.getBundleItems()) {

						// create a new context, to force the calculator to consider the sub-seller, instead of the subscriber
						Map<String, String> context = new HashMap<>();
						context.put("sellerId", sellerId);

						Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), childItem);
						childCalc.setCalculatorContext(context);						

						RevenueItem childRevenueItem = childCalc.compute(timePeriod, computeContext);
						if (childRevenueItem != null) {
							sellerRevenueItem.addRevenueItem(childRevenueItem);
						}
					}
					outputItem.addRevenueItem(sellerRevenueItem);
				}
			} catch(Exception e) {
				logger.error("Unable to retrieve bills for sellers behind marketplace {}", this.getSubscription().getSubscriberId());
				logger.error(e.getMessage());
				logger.error(e.getStackTrace().toString());
			}
		}
		else {
			// implement here other potential forEachMetrics
		}

//		if(outputItem.getItems()!=null && !outputItem.getItems().isEmpty())
			return outputItem;
//		else
//			return null;
	}

	private String getOrganisationLabel(String orgId) throws Exception {
		String orgLabel = "";
		Organization seller = this.tmfDataRetriever.getOrganization(orgId);
		if(seller!=null) {
			if(seller.getName()!=null)
				orgLabel = seller.getName();
			if(seller.getTradingName()!=null)
				orgLabel = seller.getTradingName();
		}
		orgLabel += " ("+orgId+")";
		return orgLabel.trim();
	}

}
