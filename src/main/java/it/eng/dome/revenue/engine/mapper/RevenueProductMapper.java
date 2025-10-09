package it.eng.dome.revenue.engine.mapper;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.utils.TmfConverter;
import it.eng.dome.tmforum.tmf637.v4.model.*;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RevenueProductMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueProductMapper.class);

	/*
	 * SUBSCRIPTION TO PRODUCT
	 */
	
	// Used in toACBR of RevenueBillingMapper
    public static ProductRef toProductRef(Subscription subscription) {
        if (subscription == null) return null;

        ProductRef ref = new ProductRef();
        ref.setId(subscription.getId());
        ref.setHref(subscription.getHref());
        ref.setName(subscription.getName());
      
        return ref;
    }	

	/*
	 * PRODUCT TO SUBSCRIPTION
	 */
    
	public static Subscription toSubscription(Product product) {
		
		logger.debug("Converting Product {} to Subscription", product.getId());
		
		Subscription sub = new Subscription();
		
		// general metadata
		sub.setId(product.getId());
		sub.setHref(product.getHref());
		sub.setName(product.getName());
		sub.setStartDate(product.getStartDate());
		ProductStatusType status = product.getStatus();
		if(status!=null)
			sub.setStatus(status.toString()); //convert status
		
		// related parties
		sub.setRelatedParties(TmfConverter.convertRpTo678(product.getRelatedParty()));
				
		// the product offering
		String offeringId = null;
		ProductOfferingRef offeringRef = product.getProductOffering();
		if(offeringRef!=null)
			offeringId = offeringRef.getId();
		if(offeringId==null || offeringId.isBlank()) {
			logger.warn("No productOfferingId for product {}" ,product.getId());
			return null;
		}

		// product offering price
		String productOfferingPriceId = null;
		List<ProductPrice> prices = product.getProductPrice();
		if(prices!=null && !prices.isEmpty()) {
			// ASSUMPTION: considering only the first price
			ProductOfferingPriceRef pop = prices.get(0).getProductOfferingPrice();
			if(pop!=null) {
				productOfferingPriceId = pop.getId();
			}
		}
		if(productOfferingPriceId==null || productOfferingPriceId.isBlank()) {
			logger.warn("No productOfferingPriceId for product {}" ,product.getId());
			return null;
		}

		// finally the plan
		Plan plan = new Plan();
		plan.setId(plan.generateId(offeringId, productOfferingPriceId));
		sub.setPlan(plan);

		// characteristics
		Map<String,String> characteristics = new HashMap<>();
		List<Characteristic> productCharacteristic = product.getProductCharacteristic();
		if (productCharacteristic != null) {
		    for (Characteristic ch: productCharacteristic) {
		        String key = ch.getName();
		        Object val = ch.getValue();
		        String value;
		        if (val instanceof Boolean) {
		            value = ((Boolean) val).toString();
		        } else {
		            value = val != null ? val.toString() : null;
		        }
		        characteristics.put(key, value);
		    }
		}
		sub.setCharacteristics(characteristics);

		return sub;
	}
}
