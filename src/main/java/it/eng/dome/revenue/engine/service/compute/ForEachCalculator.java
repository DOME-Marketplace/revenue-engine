package it.eng.dome.revenue.engine.service.compute;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
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

    public RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext) throws ExternalServiceException, BadTmfDataException {

		// prepare the output
		RevenueItem outputItem = new RevenueItem(this.item.getName(), this.item.getCurrency());

		// extract the foreach property
		String iterateOver = this.item.getForEachMetric();
		if(iterateOver==null || iterateOver.isBlank()) {
			logger.error("Found a 'foreach' price bundle without a 'forEachMetric' property. Skipping the whole bundle price");
			return null;
		}

		// now process them
		if("activeSellersBehindMarketplace".equalsIgnoreCase(iterateOver)) {
			// retrieve the possible values
			List<String> activeSellerIds = this.metricsRetriever.getDistinctValuesForKey(iterateOver, this.getSubscription().getSubscriberId(), timePeriod);

			logger.debug("Found {} sellers + marketplace {} in period {}", activeSellerIds.size(), this.getSubscription().getSubscriberId(), timePeriod);

			// foreach 'iterator' property, build a sub-revenueItem with all child prices computed with the 'iterator' property.
			for(String activeSellerId: activeSellerIds) {

				String label = this.getLabel(activeSellerId);
				logger.debug("looking for transactions of seller {} in period {}", label, timePeriod);

				for (PlanItem childItem : this.item.getBundleItems()) {

					Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), childItem, this);
					// update the context, to force the calculator to consider the sub-seller, instead of the subscriber
					childCalc.getCalculatorContext().put("sellerId", activeSellerId);
					childCalc.getCalculatorContext().put("sellerBehindMarketplace.id", activeSellerId);
					childCalc.getCalculatorContext().put("sellerBehindMarketplace.name", label);

					RevenueItem childRevenueItem = childCalc.compute(timePeriod, computeContext);
					if (childRevenueItem != null) {
						outputItem.addRevenueItem(childRevenueItem);
					}
				}
			}
		}
		else if("billedSellersBehindMarketplace".equalsIgnoreCase(iterateOver)) {
			// iterate over bills issued by a federarted marketplace to its own members
			String federatedMarketplaceId = this.getSubscription().getSubscriberId();
			String federatedMarketplaceLabel = this.getLabel(federatedMarketplaceId);

			List<String> billedSellersBehindMarketplace = this.metricsRetriever.getDistinctValuesForKey(iterateOver, federatedMarketplaceId, timePeriod);
			logger.debug("Found {} sellers {} in period {}", billedSellersBehindMarketplace.size(), federatedMarketplaceLabel, timePeriod);

			// foreach 'iterator' property, build a sub-revenueItem with all child prices computed with the 'iterator' property.
			for(String billedSellerId: billedSellersBehindMarketplace) {

				String billedSellerLabel = this.getLabel(billedSellerId);
				logger.debug("looking for revenue bills issued by federated marketplace '{}' to seller '{}'' in period {}", federatedMarketplaceLabel, billedSellerLabel, timePeriod);

				for (PlanItem childItem : this.item.getBundleItems()) {

					Calculator childCalc = CalculatorFactory.getCalculatorFor(this.getSubscription(), childItem, this);

					// update the context, to force the calculator to consider the sub-seller, instead of the subscriber
					childCalc.getCalculatorContext().put("sellerId", federatedMarketplaceId);
					childCalc.getCalculatorContext().put("buyerId", billedSellerId);
					childCalc.getCalculatorContext().put("sellerBehindMarketplace.id", billedSellerId);
					childCalc.getCalculatorContext().put("sellerBehindMarketplace.name", billedSellerLabel);

					RevenueItem childRevenueItem = childCalc.compute(timePeriod, computeContext);
					if (childRevenueItem != null) {
						outputItem.addRevenueItem(childRevenueItem);
					}
					
				}
			}
		}
		else {
			logger.error("Metric {} is not supported.", iterateOver);
			return null;
		}

		return outputItem;

	}

	private String getLabel(String id) throws BadTmfDataException, ExternalServiceException {
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
