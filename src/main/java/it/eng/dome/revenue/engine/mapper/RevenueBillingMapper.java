package it.eng.dome.revenue.engine.mapper;

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
	
	public static List<AppliedCustomerBillingRate> toACBRList(SimpleBill sb, Subscription subscription, BillingAccountRef billingAccountRef) {
	    if (sb == null || sb.getRevenueItems() == null || sb.getRevenueItems().isEmpty()) {
	        return Collections.emptyList();
	    }

	    List<AppliedCustomerBillingRate> acbrList = new ArrayList<>();
	    for (RevenueItem item : sb.getRevenueItems()) {
	        try {
	            if (item.getOverallValue() != null && item.getOverallValue() != 0.0) { //for ignore item with value null or zero
	                acbrList.add(toACBR(item, sb, subscription, billingAccountRef));
	            } else {
	            	logger.debug("Skipping RevenueItem with null or zero value: {}", item.getName());
	            }
	        } catch (Exception e) {
	        	logger.error("Failed to map RevenueItem '{}' to AppliedCustomerBillingRate: {}", item.getName(), e.getMessage(), e);
	        }
	    }
	    return acbrList;
	}

	public static AppliedCustomerBillingRate toACBR(RevenueItem item, SimpleBill sb, Subscription subscription, BillingAccountRef billingAccountRef) {
	    if (item == null) {
	    	logger.warn("Cannot map to AppliedCustomerBillingRate: RevenueItem is null");
	    	return null;
	    }
	    
	    if (sb == null || sb.getPeriod() == null) {
	        throw new IllegalArgumentException("SimpleBill or its period must not be null");
	    }

	    AppliedCustomerBillingRate acbr = new AppliedCustomerBillingRate();
	    acbr.setId(UUID.randomUUID().toString());
	    acbr.setHref(acbr.getId());
	    acbr.setName("Applied Customer Billing Rate of " + item.getName());
	    acbr.setDescription("Applied Customer Billing Rate of " 
	        + (subscription != null ? subscription.getName() : "") 
	        + " for period " + sb.getPeriod().getStartDateTime() + " - " + sb.getPeriod().getEndDateTime());
	    acbr.setDate(subscription != null ? subscription.getStartDate() : sb.getPeriod().getStartDateTime());
	    acbr.setIsBilled(false);
	    acbr.setType(null);
	    acbr.setPeriodCoverage(sb.getPeriod());
	    acbr.setBill(null);

	    acbr.setProduct(subscription != null ? toProductRef(subscription) : null);
	    acbr.setBillingAccount(billingAccountRef);

	    if (subscription != null && subscription.getRelatedParties() != null) {
	        acbr.setRelatedParty(subscription.getRelatedParties());
	    } else {
	        acbr.setRelatedParty(sb.getRelatedParties());
	    }

	    if (item.getOverallValue() != null) {
	        Money money = new Money();
	        money.setValue(item.getOverallValue().floatValue());
	        money.setUnit(item.getCurrency());
	        acbr.setTaxExcludedAmount(money);
	    } else {
	        logger.debug("RevenueItem '{}' has no overall value set", item.getName());
	    }

	    if (item.getItems() != null) {
	        List<AppliedBillingRateCharacteristic> characteristics = new ArrayList<>();
	        for (RevenueItem child : item.getItems()) {
	            collectCharacteristics(child, characteristics);
	        }
	        acbr.setCharacteristic(characteristics);
	    }

	    return acbr;
	}
	
	public static CustomerBill toCB(SimpleBill simpleBill, BillingAccountRef billingAccountRef) {
		if (simpleBill == null) {
		    logger.error("toCB: simpleBill is null, cannot map to CustomerBill");
		    throw new IllegalArgumentException("simpleBill cannot be null");
		}
		if (billingAccountRef == null) {
		    logger.warn("toCB: billingAccountRef is null, CustomerBill will have null billingAccount");
		}
		
		CustomerBill cb = new CustomerBill();

        // 1. id and basic metadata
        String billId = simpleBill.getId();
        cb.setId(billId);
        cb.setHref(billId);
        cb.setBillNo(billId.substring(billId.lastIndexOf(":") + 1, billId.length()).substring(0, 6));

        // 2. Date
        cb.setBillDate(simpleBill.getBillTime());
        cb.setLastUpdate(OffsetDateTime.now());
        cb.setNextBillDate(simpleBill.getPeriod().getEndDateTime().plusMonths(1));
        cb.setPaymentDueDate(simpleBill.getPeriod().getEndDateTime().plusDays(10)); // Q: How many days after the invoice date should we set the due date?

        // 3. period
        cb.setBillingPeriod(simpleBill.getPeriod());

        // 4. amounts
        Float amountTaxExcluded = simpleBill.getAmount().floatValue(); // Q: Is sbAmount whitout tax?
        Float taxRate = 0.20f; // Q: How do we set the TaxRate?
        Float taxAmount = amountTaxExcluded * taxRate;
        Float amountIncludedTax = amountTaxExcluded + taxAmount;

        cb.setAmountDue(createMoneyTmF678(0.0f, "EUR")); //TODO: ask Stefania
        cb.setRemainingAmount(createMoneyTmF678(0.0f, "EUR")); //TODO: ask Stefania
        cb.setTaxIncludedAmount(createMoneyTmF678(amountIncludedTax, "EUR"));
        cb.setTaxExcludedAmount(createMoneyTmF678(amountTaxExcluded, "EUR"));

        // 5. tax
        TaxItem taxItem = new TaxItem()
                .taxAmount(createMoneyTmF678(taxAmount, "EUR"))
                .taxCategory("VAT")
                .taxRate(taxRate);
        cb.setTaxItem(List.of(taxItem));

        // 6. Related Party
        cb.setRelatedParty(simpleBill.getRelatedParties());

        // 7. Other default attributes
        cb.setCategory("normal");
        cb.setRunType("onCycle"); // onCycle or offCycle?
        cb.setState(StateValue.NEW);
//        cb.setEstimated(simpleBill.isEstimated());

        // 8. Placeholder
        cb.setBillingAccount(billingAccountRef);
        cb.setFinancialAccount(null);
        cb.setAppliedPayment(new ArrayList<>());
        cb.setBillDocument(new ArrayList<>());
        cb.setPaymentMethod(null);

//        // 9. Type and metadata
//        cb._type("CustomerBill");
//        cb._baseType("CustomerBill");
//        cb._schemaLocation("...some uri...");
        return cb;
    }

	public static ProductOffering toProductOffering(Plan plan) {
		if (plan == null) {
		    logger.error("toProductOffering: plan is null, returning null ProductOffering");
		    return null;
		}

	    ProductOffering po = new ProductOffering();

	    // Basic fields
	    po.setId(plan.getId());
	    po.setHref(plan.getId());
	    po.setName(plan.getName());
	    po.setDescription(plan.getDescription());
	    po.setLifecycleStatus(plan.getLifecycleStatus());
	    po.setLastUpdate(OffsetDateTime.now()); // or create a last update in plan
	    po.setVersion("1.0"); // TODO: understand how manage this attribute

	    // Time validity
	    if (plan.getValidFor() != null) {
	        TimePeriod validFor = new TimePeriod();
	        validFor.setStartDateTime(plan.getValidFor().getStartDateTime());
	        validFor.setEndDateTime(plan.getValidFor().getEndDateTime());
	        po.setValidFor(validFor);
	    }

	    // isBundle - default to false
	    po.setIsBundle(false); // TODO: understand how manage this attribute

	    // Price
//	    if (plan.getPrice() != null) {
//	        ProductOfferingPrice price = new ProductOfferingPrice();
//	        price.setName(plan.getName() + " Price");
//	        price.setDescription("Plan price");
//	        price.setPriceType("recurring"); // understand how manage this attribute
//	        
//	        // Set recurring charge period type and length
//	        if (plan.getBillingPeriodType() != null) {
//	            price.setRecurringChargePeriodType(plan.getBillingPeriodType().name().toLowerCase());
//	        }
//	        if (plan.getBillingPeriodLength() != null) {
//	            price.setRecurringChargePeriodLength(plan.getBillingPeriodLength());
//	        }
//	        
//	        price.setPrice(createMoneyTmF620(plan.getPrice().getAmount().floatValue(), "EUR"));
//	        po.setProductOfferingPrice(List.of(price));
//	    }

	    // Product Specification reference (mock)
	    ProductSpecificationRef psRef = new ProductSpecificationRef();
	    psRef.setId("urn:example:product-specification:" + plan.getId()); // TODO: understand how to manage this attribute
	    psRef.setName(plan.getName() + " Specification");
	    psRef.setVersion("0.1"); // TODO: understand how to manage this attribute
	    //psRef.setHref(psRef.getId());
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

	private static it.eng.dome.tmforum.tmf620.v4.model.Money createMoneyTmF620(Float amount, String currency) {
	    it.eng.dome.tmforum.tmf620.v4.model.Money money = new it.eng.dome.tmforum.tmf620.v4.model.Money();
	    money.setValue(amount);
	    money.setUnit(currency);
	    return money;
	}

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

