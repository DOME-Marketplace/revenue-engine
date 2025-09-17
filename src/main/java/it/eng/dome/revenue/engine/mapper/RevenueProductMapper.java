package it.eng.dome.revenue.engine.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.utils.TmfConverter;
import it.eng.dome.tmforum.tmf620.v4.model.Money;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPriceRefOrValue;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductOfferingPriceRef;
import it.eng.dome.tmforum.tmf637.v4.model.ProductOfferingRef;
import it.eng.dome.tmforum.tmf637.v4.model.ProductPrice;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;

public class RevenueProductMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueProductMapper.class);
	
    /*
	 * PLAN TO PRODUCT OFFERING
	*/
	
	// FIXME: Do we need the plan mapper?
	
//	public static ProductOffering toProductOffering(Plan plan) {
//		if (plan == null) {
//		    logger.error("toProductOffering: plan is null, returning null ProductOffering");
//		    return null;
//		}
//
//	    ProductOffering po = new ProductOffering();
//	    
//	    po.setId(plan.getId());
//	    po.setHref(plan.getId());
//	    po.setName(plan.getName());
//	    po.setDescription(plan.getDescription());
//	    po.setLifecycleStatus(plan.getLifecycleStatus());
//	    po.setIsBundle(false);
//	    // if true, it will have other productOffering inside
//	    // must fill productOffering relationship
//	    po.setLastUpdate(OffsetDateTime.now());
//	    po.setVersion("1.0");
//
//	    if (plan.getValidFor() != null) {
//	        TimePeriod validFor = new TimePeriod();
//	        validFor.setStartDateTime(plan.getValidFor().getStartDateTime());
//	        validFor.setEndDateTime(plan.getValidFor().getEndDateTime());
//	        po.setValidFor(validFor);
//	    }
//	    
//		// reference
//
//	    // Product Offering Price mapping
//        if (plan.getPrice() != null) {
//            // Use the public method to map and collect all prices from the hierarchical structure.
//            List<ProductOfferingPrice> rawMappedPrices = mapAllPrices(plan.getPrice());
//
//            // Convert the list of ProductOfferingPrice to List<ProductOfferingPriceRefOrValue> using the new dedicated helper method.
//            List<ProductOfferingPriceRefOrValue> finalMappedPrices = toProductOfferingPriceRefOrValueList(rawMappedPrices);
//            
//            // Set the final list of wrapped prices to the ProductOffering.
//            if (!finalMappedPrices.isEmpty()) {
//                po.setProductOfferingPrice(finalMappedPrices);
//            }
//        }
//
//	    // Product Specification reference (MUSTTT)
//	    ProductSpecificationRef psRef = new ProductSpecificationRef();
//	    psRef.setId("urn:example:product-specification:" + plan.getId());
//	    psRef.setName(plan.getName() + " Specification");
//	    psRef.setVersion("0.1");
//	    try {
//			psRef.setHref(new URI(psRef.getId()));
//		} catch (URISyntaxException e) {
//			 logger.error("Invalid URI syntax for ProductSpecificationRef Href with ID '{}'", psRef.getId(), e);
//        throw new IllegalArgumentException("Failed to create ProductSpecificationRef Href due to invalid URI syntax", e);
//		}
//	    po.setProductSpecification(psRef);
//
//	    // Product Offering Terms (e.g., contract duration, renewal)
//	    List<ProductOfferingTerm> terms = new ArrayList<>();
//	    if (plan.getContractDurationLength() != null && plan.getContractDurationPeriodType() != null) {
//	        ProductOfferingTerm term = new ProductOfferingTerm();
//	        term.setName("Contract Duration");
//	        term.setDescription("Minimum duration of the plan");
//	        //OPTIONAL
////	        TimePeriod duration = new TimePeriod();
////	        term.setValidFor(duration);
////	        Duration contractDuration = Duration.of(plan.getContractDurationLength(),
////	            convertToChronoUnit(plan.getContractDurationPeriodType()));
////	        term.setDuration(contractDuration);
//	        terms.add(term);
//	    }
//
//	    if (!terms.isEmpty()) {
//	        po.setProductOfferingTerm(terms);
//	    }
//	    
//	    // Category mapping - if Exists
//	    // add category to plan
//
//	    return po;
//	}
	
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
	
    /**
     * Converts a list of {@link ProductOfferingPrice} objects into a list of {@link ProductOfferingPriceRefOrValue} objects.
     * This method is necessary because the TMF620 ProductOffering model expects a list of references or embedded values, and in this specific generated client
     * model, it appears to only support references (ID, Href, Name).
     *
     * @param rawMappedPrices The list of {@link ProductOfferingPrice} objects to convert.
     * @return A {@code List} of {@link ProductOfferingPriceRefOrValue} objects.
     * @throws IllegalArgumentException if an invalid URI syntax is encountered when creating the Href.
     */
//    private static List<ProductOfferingPriceRefOrValue> toProductOfferingPriceRefOrValueList(List<ProductOfferingPrice> rawMappedPrices) {
//        List<ProductOfferingPriceRefOrValue> finalMappedPrices = new ArrayList<>();
//        for (ProductOfferingPrice price : rawMappedPrices) {
//        	
//            ProductOfferingPriceRefOrValue refOrValue = new ProductOfferingPriceRefOrValue();
//
//            refOrValue.setId(price.getId());
//            try {
//				refOrValue.setHref(new URI(price.getHref()));
//			} catch (URISyntaxException e) {
//				logger.error("Invalid URI syntax for ProductOfferingPriceRefOrValue Href with ID '{}'", price.getId(), e);
//                throw new IllegalArgumentException("Failed to create ProductOfferingPriceRefOrValue Href due to invalid URI syntax", e);
//			}
//            refOrValue.setName(price.getName());
//            
//            finalMappedPrices.add(refOrValue);
//        }
//        return finalMappedPrices;
//    }
//    
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
		sub.setStatus(product.getStatus().toString()); //convert status
		
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
		// FIXME: use a clever packing of ids
		plan.setId("urn:ngsi-ld:plan:"+offeringId+productOfferingPriceId);
		sub.setPlan(plan);

		/*
		if(plan == null) {
			logger.error("No plan can be retrieved for offering {} and price {}" , offeringId, productOfferingPriceId);
			return null;

		}
		*/
		
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

//	public static ProductOffering fromPlanToProductOffering(Plan plan) {
//        try {
//            ProductOffering offering = new ProductOffering();
//            offering.setId("urn:tmf:product-offering:" + plan.getId());
//            offering.setHref("urn:tmf:product-offering:" + plan.getId());
//            offering.setName(plan.getName() + " Offering");
//            offering.setDescription(plan.getDescription());
//            offering.setLifecycleStatus(plan.getLifecycleStatus());
//            offering.setIsBundle(false);
//
//            ProductSpecificationRef specRef = new ProductSpecificationRef();
//            specRef.setId(plan.getId());
//            specRef.setName(plan.getName() + " Plan Specification");
//            specRef.setHref(new URI(plan.getId()));
//            specRef.setAtType("PlanSpecRef");
//            specRef.setVersion("1.0");
//
//            offering.setProductSpecification(specRef);
//
//            if (plan.getValidFor() != null) {
//                ProductOfferingTerm term = new ProductOfferingTerm();
//                term.setName(plan.getName());
//                term.setDescription(plan.getDescription());
//                term.setValidFor(TmfConverter.convertTPto620(plan.getValidFor()));
//                offering.setProductOfferingTerm(List.of(term));
//            }
//
//            return offering;
//
//        } catch (Exception e) {
//            logger.error("Error converting Plan to ProductOffering: {}", plan.getId(), e);
//            throw new RuntimeException("Mapping Plan to ProductOffering failed", e);
//        }
//    }
}
