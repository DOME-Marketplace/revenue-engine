package it.eng.dome.revenue.engine.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.utils.TmfConverter;
import it.eng.dome.tmforum.tmf620.v4.model.Money;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf637.v4.model.Characteristic;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductOfferingPriceRef;
import it.eng.dome.tmforum.tmf637.v4.model.ProductOfferingRef;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;

public class RevenueProductMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueProductMapper.class);
	
	/**
     * Entry point for mapping a hierarchical {@code Price} structure into a flat list of {@link ProductOfferingPrice} objects.
     * This method initializes the list and then delegates the recursive collection
     * to the private helper method {@link #collectAndMapPrices(it.eng.dome.revenue.engine.model.Price, List)}.
     *
     * @param planRootPrice The root {@code Price} object from the Plan's price hierarchy.
     * @return A {@code List} of {@link ProductOfferingPrice} objects, representing all valid leaf prices.
     */
    public static List<ProductOfferingPrice> mapAllPrices(it.eng.dome.revenue.engine.model.Price planRootPrice) {
        List<ProductOfferingPrice> mappedPrices = new ArrayList<>();
        if (planRootPrice != null) {
            collectAndMapPrices(planRootPrice, mappedPrices);
        }
        return mappedPrices;
    }

    private static void collectAndMapPrices(it.eng.dome.revenue.engine.model.Price sourcePrice, List<ProductOfferingPrice> targetPrices) {
        // Base case for the recursion: if the price has no children, it's a leaf node.
        if (sourcePrice.getPrices() == null || sourcePrice.getPrices().isEmpty()) {
            // Only map the leaf node if its amount is valid (not null and greater than 0).
            if (sourcePrice.getAmount() != null && sourcePrice.getAmount().floatValue() > 0) {
                ProductOfferingPrice price = new ProductOfferingPrice();
                
                // Set basic price metadata
                String priceId = UUID.randomUUID().toString();
                price.setId(priceId);
                price.setHref(priceId);
                price.setName(sourcePrice.getName() + " Price");
                price.setDescription(sourcePrice.getName() + " price");
                
                // Determine the price type, prioritizing the local value.
                if (sourcePrice.getType() != null) {
                    price.setPriceType(sourcePrice.getType().name().toLowerCase());
                } else {
                     // Use a default price type if none is specified.
                    price.setPriceType("recurring"); 
                }
                
                // Set the recurring charge period type and length, potentially inherited from parent nodes.
                if (sourcePrice.getRecurringChargePeriodType() != null) {
                    price.setRecurringChargePeriodType(sourcePrice.getRecurringChargePeriodType().name().toLowerCase());
                }
                if (sourcePrice.getRecurringChargePeriodLength() != null) {
                    price.setRecurringChargePeriodLength(sourcePrice.getRecurringChargePeriodLength());
                }
                
                // Set the price amount and currency.
                if (sourcePrice.getAmount() != null) {
                    price.setPrice(createMoneyTmF620(sourcePrice.getAmount().floatValue(), "EUR"));
                }

                // Add the fully mapped price to the target list.
                targetPrices.add(price);
            }
        } else {
            // Recursive case: if the item has children, traverse them.
            for (it.eng.dome.revenue.engine.model.Price childPrice : sourcePrice.getPrices()) {
                collectAndMapPrices(childPrice, targetPrices);
            }
        }
    }
	
	private static Money createMoneyTmF620(Float amount, String currency) {
		Money money = new it.eng.dome.tmforum.tmf620.v4.model.Money();
		money.setValue(amount);
		money.setUnit(currency);
		return money;
	 }
	
	/*
	 * SUBSCRIPTION TO PRODUCT
	 */
	
	// Used in toACBR of RevenueBillingMapper
    public static ProductRef toProductRef(Subscription subscription) {
        if (subscription == null) return null;

        ProductRef ref = new ProductRef();
        ref.setId(subscription.getId());
        ref.setHref(subscription.getId()); //TODO: change this with href when sub will contains href
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

		/*
		if(plan == null) {
			logger.error("No plan can be retrieved for offering {} and price {}" , offeringId, productOfferingPriceId);
			return null;

		}
		*/
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

	/*
	private static String pack(String prefix, String... ids) {
		String packed = prefix;
		for(String id: ids) {
			packed+="id";
		}
		return packed;
	}

	private static List<String> unpack(String packedId) {
		// extract the prefix (urn:ngsi-ld:plan:) ... until found a digit
		String s = new String("Str87uyuy232");
		Matcher matcher = Pattern.compile("[\\d+]").matcher(packedId);
		while(matcher.find()) {
			matcher.find();
			int i = Integer.valueOf(matcher.group());
		}
	}
		*/
}
