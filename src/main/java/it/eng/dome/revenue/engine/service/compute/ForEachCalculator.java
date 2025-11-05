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

		// only 'activeMarketplaceIncludedMarketplace' is currently supported
		if(!"activeSellersIncludedMarketplace".equalsIgnoreCase(iterateOver)) {
			logger.error("{} is not supported. Currently, only 'marketplaceSeller' metric is supported.");
			return null;
		}

		// now process them
		if("activeSellersIncludedMarketplace".equalsIgnoreCase(iterateOver)) {
			// retrieve the possible values
			try {
				List<String> activeSellerIds = this.metricsRetriever.getDistinctValuesForKey(iterateOver, this.getSubscription().getSubscriberId(), timePeriod);

				logger.debug("Found {} sellers + marketplace {} in period {}", activeSellerIds.size(), this.getSubscription().getSubscriberId(), timePeriod);

				// foreach 'iterator' property, build a sub-revenueItem with all child prices computed with the 'iterator' property.
				for(String activeSellerId: activeSellerIds) {

					String label = this.getLabel(activeSellerId);

					logger.debug("looking for transactions of seller {} in period {}", label, timePeriod);
//					RevenueItem sellerRevenueItem = new RevenueItem(this.item.getName() + " - Share from '" + label + "'", this.item.getCurrency());

//					if(this.item.getType()!=null)
//						sellerRevenueItem.setType(this.item.getType().toString());

					for (PlanItem childItem : this.item.getBundleItems()) {


						Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), childItem, this);
						// update the context, to force the calculator to consider the sub-seller, instead of the subscriber
						childCalc.getCalculatorContext().put("sellerId", activeSellerId);

						RevenueItem childRevenueItem = childCalc.compute(timePeriod, computeContext);
						// prefix the name with the label of the iterator
						childRevenueItem.setName(childRevenueItem.getName() + " computed for " + label);
						if (childRevenueItem != null) {
							outputItem.addRevenueItem(childRevenueItem);
						}
					}

//					outputItem.addRevenueItem(sellerRevenueItem);
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

	private String getLabel(String id) throws Exception {
		if(id.startsWith("urn:ngsi-ld:organization")) {
			String orgLabel = "";
			Organization seller = this.tmfDataRetriever.getOrganization(id);
			if(seller!=null) {
				if(seller.getName()!=null)
					orgLabel = seller.getName();
				if(seller.getTradingName()!=null)
					orgLabel = seller.getTradingName();
			}
			return orgLabel.trim();
		}
		else {
			return id;
		}
	}

}
