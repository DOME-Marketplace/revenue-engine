package it.eng.dome.revenue.engine.mapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf620.v4.model.Money;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPriceRefOrValue;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingTerm;
import it.eng.dome.tmforum.tmf620.v4.model.ProductSpecificationRef;
import it.eng.dome.tmforum.tmf620.v4.model.TimePeriod;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;

public class RevenueProductMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueProductMapper.class);
	
	public static ProductOffering toProductOffering(Plan plan) {
		if (plan == null) {
		    logger.error("toProductOffering: plan is null, returning null ProductOffering");
		    return null;
		}

	    ProductOffering po = new ProductOffering();

	    // id and basic metadata
	    po.setId(plan.getId());
	    po.setHref(plan.getId());
	    po.setName(plan.getName());
	    po.setDescription(plan.getDescription());
	    po.setLifecycleStatus(plan.getLifecycleStatus());
	    po.setLastUpdate(OffsetDateTime.now()); // or create a last update in plan
	    po.setVersion("1.0"); // TODO: understand how manage this attribute

	    if (plan.getValidFor() != null) {
	        TimePeriod validFor = new TimePeriod();
	        validFor.setStartDateTime(plan.getValidFor().getStartDateTime());
	        validFor.setEndDateTime(plan.getValidFor().getEndDateTime());
	        po.setValidFor(validFor);
	    }
	    
	    po.setIsBundle(false); // TODO: understand how manage this attribute

		// reference

	    // Product Offering Price mapping
        if (plan.getPrice() != null) {
            // Use the public method to map and collect all prices from the hierarchical structure.
            List<ProductOfferingPrice> rawMappedPrices = mapAllPrices(plan.getPrice());

            // Convert the list of ProductOfferingPrice to List<ProductOfferingPriceRefOrValue>
            // using the new dedicated helper method.
            List<ProductOfferingPriceRefOrValue> finalMappedPrices = toProductOfferingPriceRefOrValueList(rawMappedPrices);
            
            // Set the final list of wrapped prices to the ProductOffering.
            if (!finalMappedPrices.isEmpty()) {
                po.setProductOfferingPrice(finalMappedPrices);
            }
        }

	    // Product Specification reference
	    ProductSpecificationRef psRef = new ProductSpecificationRef();
	    psRef.setId("urn:example:product-specification:" + plan.getId()); // TODO: understand how to manage this attribute
	    psRef.setName(plan.getName() + " Specification");
	    psRef.setVersion("0.1"); // TODO: understand how to manage this attribute
	    try {
			psRef.setHref(new URI(psRef.getId()));
		} catch (URISyntaxException e) {
			 logger.error("Invalid URI syntax for ProductSpecificationRef Href with ID '{}'", psRef.getId(), e);
        throw new IllegalArgumentException("Failed to create ProductSpecificationRef Href due to invalid URI syntax", e);
		}
	    po.setProductSpecification(psRef);

	    // Product Offering Terms (e.g., contract duration, renewal)
	    List<ProductOfferingTerm> terms = new ArrayList<>();

	    if (plan.getContractDurationLength() != null && plan.getContractDurationPeriodType() != null) {
	        ProductOfferingTerm term = new ProductOfferingTerm();
	        term.setName("Contract Duration");
	        term.setDescription("Minimum duration of the plan");
	        //OPTIONAL
//	        TimePeriod duration = new TimePeriod();
//	        term.setValidFor(duration);
//
//	        Duration contractDuration = Duration.of(plan.getContractDurationLength(),
//	            convertToChronoUnit(plan.getContractDurationPeriodType()));
//	        term.setDuration(contractDuration);
	        terms.add(term);
	    }

	    if (!terms.isEmpty()) {
	        po.setProductOfferingTerm(terms);
	    }
	    
	    // Category mapping - if Exists
//	    if (plan.getCategories() != null && !plan.getCategories().isEmpty()) {
//	        List<CategoryRef> categoryRefs = plan.getCategories().stream()
//	            .map(cat -> {
//	                CategoryRef catRef = new CategoryRef();
//	                catRef.setId(cat.getId());
//	                catRef.setHref(cat.getHref());
//	                catRef.setName(cat.getName());
//	                return catRef;
//	            })
//	            .toList();
//	        po.setCategory(categoryRefs);
//	    }

	    return po;
	}
	
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
    private static List<ProductOfferingPriceRefOrValue> toProductOfferingPriceRefOrValueList(List<ProductOfferingPrice> rawMappedPrices) {
        List<ProductOfferingPriceRefOrValue> finalMappedPrices = new ArrayList<>();
        for (ProductOfferingPrice price : rawMappedPrices) {
        	
            ProductOfferingPriceRefOrValue refOrValue = new ProductOfferingPriceRefOrValue();

            refOrValue.setId(price.getId());
            try {
				refOrValue.setHref(new URI(price.getHref()));
			} catch (URISyntaxException e) {
				logger.error("Invalid URI syntax for ProductOfferingPriceRefOrValue Href with ID '{}'", price.getId(), e);
                throw new IllegalArgumentException("Failed to create ProductOfferingPriceRefOrValue Href due to invalid URI syntax", e);
			}
            refOrValue.setName(price.getName());
            
            //TODO: ADD OTHER ATTR. IF IT IS NEED.
            
            finalMappedPrices.add(refOrValue);
        }
        return finalMappedPrices;
    }
    
	private static Money createMoneyTmF620(Float amount, String currency) {
		Money money = new it.eng.dome.tmforum.tmf620.v4.model.Money();
		money.setValue(amount);
		money.setUnit(currency);
		return money;
	 }
	
	
	public static Product toProduct(Subscription subscription, it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef billingAccountRef) {
		
		logger.debug("Converting Subscription to Product: {}", subscription.getId());
		
		Product product = new Product();
		
		product.setId(subscription.getId());
		product.setName(subscription.getName());
		product.setDescription("Product for " + subscription.getName());
		product.setHref(subscription.getId());
		product.setIsBundle(false); // ??
		product.isCustomerVisible(false); // ??
		product.orderDate(OffsetDateTime.now()); // ??
		product.startDate(subscription.getStartDate()); // ??
		product.terminationDate(subscription.getStartDate().plusYears(1)); // ??
		product.setStatus(ProductStatusType.ACTIVE); // ??
		product.setProductSerialNumber(subscription.getId()); //??
		
		// reference to the product
		
		product.setRelatedParty(convertRpTo637(subscription.getRelatedParties()));
		
		product.setBillingAccount(convertBillingAccountRefTo637(billingAccountRef));
		
		// productCharacteristics ??
		
		// productOffering ??
		
		// productPrice ?? (is the same of the (plan? ProductOffering?))
		
		return product;
	}

    
	public static List<it.eng.dome.tmforum.tmf637.v4.model.RelatedParty> convertRpTo637(
            List<it.eng.dome.tmforum.tmf678.v4.model.RelatedParty> list678) {

        if (list678 == null) {
            return null;
        }

        List<it.eng.dome.tmforum.tmf637.v4.model.RelatedParty> list637 = new ArrayList<>();

        for (it.eng.dome.tmforum.tmf678.v4.model.RelatedParty rp678 : list678) {
            it.eng.dome.tmforum.tmf637.v4.model.RelatedParty rp637 = new it.eng.dome.tmforum.tmf637.v4.model.RelatedParty();

            rp637.setId(rp678.getId());
            rp637.setHref(rp678.getId());
            rp637.setName(rp678.getName());
            rp637.setRole(rp678.getRole());
            rp637.setAtReferredType(rp678.getAtReferredType()); // ??

            list637.add(rp637);
        }

        return list637;
    }

    
	public static it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef convertBillingAccountRefTo637(
			it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef billingAccountRef678) {

		if (billingAccountRef678 == null) {
			return null;
		}

		it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef billingAccountRef637 = new it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef();
		billingAccountRef637.setId(billingAccountRef678.getId());
		billingAccountRef637.setHref(billingAccountRef678.getHref());
		billingAccountRef637.setName(billingAccountRef678.getName());

		return billingAccountRef637;
	}
}
