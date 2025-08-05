package it.eng.dome.revenue.engine.mapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingTerm;
import it.eng.dome.tmforum.tmf620.v4.model.ProductSpecificationRef;
import it.eng.dome.tmforum.tmf620.v4.model.TimePeriod;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedBillingRateCharacteristic;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;
import it.eng.dome.tmforum.tmf678.v4.model.StateValue;
import it.eng.dome.tmforum.tmf678.v4.model.TaxItem;

public class RevenueBillingMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueBillingMapper.class);
	
	/**
	 * Maps a SimpleBill object to a list of AppliedCustomerBillingRate objects.
	 * This method iterates through the revenue items of the SimpleBill,
	 * and for each item, it recursively collects all leaf-level RevenueItems
	 * to be converted into AppliedCustomerBillingRate objects.
	 * @param sb The SimpleBill object containing the revenue data.
	 * @param subscription The Subscription object related to the billing.
	 * @param billingAccountRef The BillingAccountRef object for the billing account.
	 * @return A List of AppliedCustomerBillingRate objects, or an empty list if the input SimpleBill is null or has no revenue items.
	*/
	public static List<AppliedCustomerBillingRate> toACBRList(SimpleBill sb, Subscription subscription, BillingAccountRef billingAccountRef) {
	    if (sb == null || sb.getRevenueItems() == null || sb.getRevenueItems().isEmpty()) {
	        return Collections.emptyList();
	    }

	    List<AppliedCustomerBillingRate> acbrList = new ArrayList<>();
	    for (RevenueItem item : sb.getRevenueItems()) {
			// For each item, call a recursive helper method to find all "leaf" items and map them to AppliedCustomerBillingRate objects.
	        collectLeafItemsAndMap(item, sb, subscription, billingAccountRef, acbrList);
	    }
	    return acbrList;
	}
	
	/**
	 * Recursively traverses the hierarchy of a RevenueItem to find all "leaf" nodes.
	 * A leaf node is a RevenueItem that does not contain any child items.
	 * Once a leaf node is found, it is mapped to an AppliedCustomerBillingRate object
	 * and added to the provided list.
	 *
	 * @param item The current RevenueItem in the traversal.
	 * @param sb The SimpleBill object from which the item originates.
	 * @param subscription The Subscription object related to the billing.
	 * @param billingAccountRef The BillingAccountRef for the billing account.
	 * @param acbrList The list to which the generated AppliedCustomerBillingRate objects will be added.
	*/
	private static void collectLeafItemsAndMap(RevenueItem item, SimpleBill sb, Subscription subscription, BillingAccountRef billingAccountRef, List<AppliedCustomerBillingRate> acbrList) {
		// Base case for the recursion: if the item is null, simply return.
		if (item == null) return;
		
		// if had a son, iterate
		if (item.getItems() != null && !item.getItems().isEmpty()) {
			for (RevenueItem child : item.getItems()) {
				// if it has children, recursively call this method for each child.
				collectLeafItemsAndMap(child, sb, subscription, billingAccountRef, acbrList);
			}
		} else {
			// this is a leaf node (no children). Billable item that can be mapped to an ACBR.
			try {
//				if (item.getOverallValue() != null && item.getOverallValue() != 0.0) {
				// map the leaf RevenueItem to an AppliedCustomerBillingRate and add it to the list.
				acbrList.add(toACBR(item, sb, subscription, billingAccountRef));
//				} else {
//					logger.debug("Skipping RevenueItem with null or zero value: {}", item.getName());
//				}
			} catch (Exception e) {
				logger.error("Failed to map RevenueItem '{}' to AppliedCustomerBillingRate: {}", item.getName(), e.getMessage(), e);
			}
		}
	}

	/**
	 * Maps a single RevenueItem to an AppliedCustomerBillingRate (ACBR) object.
	 * This method creates a new ACBR instance and populates its fields with data
	 * from the provided RevenueItem, SimpleBill, Subscription, and BillingAccountRef.
	 *
	 * @param item The RevenueItem to be mapped. This is expected to be a "leaf" item from the bill's hierarchy.
	 * @param sb The SimpleBill object from which the RevenueItem and other context (like the billing period) originate.
	 * @param subscription The Subscription related to this billing rate, used to enrich the ACBR's details.
	 * @param billingAccountRef A reference to the billing account, to be included in the ACBR.
	 * @return A new AppliedCustomerBillingRate object populated with the provided data, or null if the input item is null.
	 * @throws IllegalArgumentException if the SimpleBill or its period are null, as these are mandatory for the ACBR.
	*/
	public static AppliedCustomerBillingRate toACBR(RevenueItem item, SimpleBill sb, Subscription subscription, BillingAccountRef billingAccountRef) {
	    if (item == null) {
	    	logger.warn("Cannot map to AppliedCustomerBillingRate: RevenueItem is null");
	    	return null;
	    }
	    
	    if (sb == null || sb.getPeriod() == null) {
	        throw new IllegalArgumentException("SimpleBill or its period must not be null");
	    }

		//id and base data
	    AppliedCustomerBillingRate acbr = new AppliedCustomerBillingRate();
	    acbr.setId(UUID.randomUUID().toString());
	    acbr.setHref(acbr.getId());
	    acbr.setName("Applied Customer Billing Rate of " + item.getName());
	    acbr.setDescription("Applied Customer Billing Rate of " 
	        + (subscription != null ? subscription.getName() : "") 
	        + " for period " + sb.getPeriod().getStartDateTime() + " - " + sb.getPeriod().getEndDateTime());
	    acbr.setDate(subscription != null ? subscription.getStartDate() : sb.getPeriod().getStartDateTime());
	    acbr.setIsBilled(false); // can we assume that it is false at start?
	    acbr.setType(null); // TODO: Which type ?
	    acbr.setPeriodCoverage(sb.getPeriod());

		//ref
		acbr.setRelatedParty(sb.getRelatedParties());
		acbr.setBill(null); // Should I set the reference with CB right away?
	    acbr.setProduct(subscription != null ? toProductRef(subscription) : null);
	    acbr.setBillingAccount(billingAccountRef);

	    if (item.getOverallValue() != null) {
	        Money money = new Money();
	        money.setValue(item.getOverallValue().floatValue());
	        money.setUnit(item.getCurrency());
	        acbr.setTaxExcludedAmount(money);
	    } else {
	        logger.debug("RevenueItem '{}' has no overall value set", item.getName());
	    }

		acbr.appliedTax(null); //??
		acbr.setTaxIncludedAmount(null);//??

	    return acbr;
	}
	
	/**
	 * Maps a SimpleBill object to a CustomerBill object, following the TMF678 specification.
	 * This method populates the CustomerBill's fields such as ID, dates, billing period,
	 * amounts, taxes, and related parties based on the provided SimpleBill.
	 *
	 * @param simpleBill The SimpleBill object containing the source data.
	 * @param billingAccountRef A reference to the billing account associated with this bill.
	 * @return A new CustomerBill object populated with the mapped data.
	 * @throws IllegalArgumentException if the provided simpleBill is null.
	*/
	public static CustomerBill toCB(SimpleBill simpleBill, BillingAccountRef billingAccountRef) {
		if (simpleBill == null) {
		    logger.error("toCB: simpleBill is null, cannot map to CustomerBill");
		    throw new IllegalArgumentException("simpleBill cannot be null");
		}
		if (billingAccountRef == null) {
		    logger.warn("toCB: billingAccountRef is null, CustomerBill will have null billingAccount");
		}
		
		CustomerBill cb = new CustomerBill();

        // id and basic metadata
        String billId = simpleBill.getId();
        cb.setId(billId);
        cb.setHref(billId);
        cb.setBillNo(billId.substring(billId.lastIndexOf(":") + 1, billId.length()).substring(0, 6));
        cb.setBillDate(simpleBill.getBillTime());
        cb.setLastUpdate(OffsetDateTime.now()); //we can assume that the last update is now
        cb.setNextBillDate(simpleBill.getPeriod().getEndDateTime().plusMonths(1)); //?
        cb.setPaymentDueDate(simpleBill.getPeriod().getEndDateTime().plusDays(10)); // Q: How many days after the invoice date should we set the due date?
        cb.setBillingPeriod(simpleBill.getPeriod());

		// other
		cb.setCategory("normal");
        cb.setRunType("onCycle"); // onCycle or offCycle?
        cb.setState(StateValue.NEW);

		// amounts
        Float amountTaxExcluded = simpleBill.getAmount().floatValue(); // Q: Is sbAmount whitout tax?
        Float taxRate = 0.20f; // Q: How do we set the TaxRate?
        Float taxAmount = amountTaxExcluded * taxRate;
        Float amountIncludedTax = amountTaxExcluded + taxAmount;
        cb.setAmountDue(createMoneyTmF678(0.0f, "EUR")); //TODO: ask Stefania
        cb.setRemainingAmount(createMoneyTmF678(0.0f, "EUR")); //TODO: ask Stefania
        cb.setTaxIncludedAmount(createMoneyTmF678(amountIncludedTax, "EUR"));
        cb.setTaxExcludedAmount(createMoneyTmF678(amountTaxExcluded, "EUR"));

		// REF
		cb.setRelatedParty(simpleBill.getRelatedParties());

        TaxItem taxItem = new TaxItem()
                .taxAmount(createMoneyTmF678(taxAmount, "EUR"))
                .taxCategory("VAT")
                .taxRate(taxRate);
        cb.setTaxItem(List.of(taxItem));        

        cb.setBillingAccount(billingAccountRef);
        cb.setFinancialAccount(null);
        cb.setAppliedPayment(new ArrayList<>());
        cb.setBillDocument(new ArrayList<>());
        cb.setPaymentMethod(null);

		// Type and metadata
       	// cb._type("CustomerBill");
       	// cb._baseType("CustomerBill");
       	// cb._schemaLocation();
        return cb;
    }

	/**
	 * Maps a {@code Plan} object to a {@code ProductOffering} object, following the TMF620 specification.
	 * This method populates the ProductOffering's fields such as ID, name, description,
	 * lifecycle status, and a reference to its product specification.
	 * <p>
	 * This mapper assumes a direct correlation between the input {@code Plan} and the
	 * output {@code ProductOffering}. It handles basic metadata, validity periods,
	 * and sets up references to the Product Specification and Product Offering Terms.
	 * </p>
	 *
	 * @param plan The {@code Plan} object containing the source data to be mapped.
	 * @return A new {@code ProductOffering} object populated with the mapped data, or {@code null} if the input plan is null.
	 * @throws IllegalArgumentException if the {@code href} for the Product Specification cannot be created due to an invalid URI syntax.
	*/
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

		// ref
	    // Price
	    // if (plan.getPrice() != null) {
	    //     ProductOfferingPrice price = new ProductOfferingPrice();
	    //     price.setName(plan.getName() + " Price");
	    //     price.setDescription("Plan price");
	    //     price.setPriceType("recurring"); // understand how manage this attribute
	        
	    //     // Set recurring charge period type and length
	    //     if (plan.getBillingPeriodType() != null) {
	    //         price.setRecurringChargePeriodType(plan.getBillingPeriodType().name().toLowerCase());
	    //     }
	    //     if (plan.getBillingPeriodLength() != null) {
	    //         price.setRecurringChargePeriodLength(plan.getBillingPeriodLength());
	    //     }
	        
	    //     price.setPrice(createMoneyTmF620(plan.getPrice().getAmount().floatValue(), "EUR"));
	    //     po.setProductOfferingPrice(List.of(price));
	    // }

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

	// private static it.eng.dome.tmforum.tmf620.v4.model.Money createMoneyTmF620(Float amount, String currency) {
	//     it.eng.dome.tmforum.tmf620.v4.model.Money money = new it.eng.dome.tmforum.tmf620.v4.model.Money();
	//     money.setValue(amount);
	//     money.setUnit(currency);
	//     return money;
	// }

	private static it.eng.dome.tmforum.tmf678.v4.model.Money createMoneyTmF678(Float amount, String currency) {
	    it.eng.dome.tmforum.tmf678.v4.model.Money money = new it.eng.dome.tmforum.tmf678.v4.model.Money();
	    money.setValue(amount);
	    money.setUnit(currency);
	    return money;
	}

    public static AppliedCustomerBillingRate toACBR(RevenueStatement rs, BillingAccountRef billingAccountRef) {
        if (rs == null) return null;

        //FIXME: assume get 0
        RevenueItem rootItem = rs.getRevenueItems().get(0);

        AppliedCustomerBillingRate acbr = new AppliedCustomerBillingRate();
        acbr.setId(UUID.randomUUID().toString());
        acbr.setHref(acbr.getId()); // check if it's ok
        acbr.setName("Applied Customer Billing Rate of " + rootItem.getName());
        acbr.setDescription("Applied Customer Billing Rate of " + rs.getDescription() + "for period " + rs.getPeriod().getStartDateTime() + "-" + rs.getPeriod().getEndDateTime());
        acbr.setDate(rs.getSubscription().getStartDate());
        acbr.setIsBilled(false);
        acbr.setType(null); //TODO: discuss
        acbr.setPeriodCoverage(rs.getPeriod());
        acbr.setBill(null); // beacuse bill will be filled when the party pays the invoice 
        
        acbr.setProduct(toProductRef(rs.getSubscription()));
        acbr.setBillingAccount(billingAccountRef);
        acbr.setRelatedParty(rs.getSubscription().getRelatedParties());
        //TODO: add appliedTax
        
        // Set taxExcludedAmount (overall value)
        if (rootItem.getOverallValue() != null) {
            Money money = new Money();
            money.setValue(rootItem.getOverallValue().floatValue());
            money.setUnit(rootItem.getCurrency());
            acbr.setTaxExcludedAmount(money);
        }
        
        //TODO: add taxIncludedAmount

        // Set characteristics (ricorsivo)
        if (rootItem.getItems() != null) {
            List<AppliedBillingRateCharacteristic> characteristics = new ArrayList<>();
            for (RevenueItem item : rootItem.getItems()) {
                collectCharacteristics(item, characteristics);
            }
            acbr.setCharacteristic(characteristics);
        }

        return acbr;
    }
    
    public static ProductRef toProductRef(Subscription subscription) {
        if (subscription == null) return null;

        ProductRef ref = new ProductRef();
        ref.setId(subscription.getId());
        ref.setHref(subscription.getId()); //TODO: change this with href when sub will contains href
        ref.setName(subscription.getName());
      
        return ref;
    }

    private static void collectCharacteristics(RevenueItem item, List<AppliedBillingRateCharacteristic> collector) {
        if (item.getValue() != null) {
            AppliedBillingRateCharacteristic c = new AppliedBillingRateCharacteristic();
            c.setName(item.getName());
            c.setValue(item.getValue());
            c.setValueType("number"); // o "decimal"
            collector.add(c);
        }

        if (item.getItems() != null) {
            for (RevenueItem child : item.getItems()) {
                collectCharacteristics(child, collector);
            }
        }
    }

    // TODO: Revert Mapping
}

