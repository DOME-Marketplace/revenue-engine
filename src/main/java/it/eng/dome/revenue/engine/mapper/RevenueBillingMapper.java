package it.eng.dome.revenue.engine.mapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.RevenueBill;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedBillingTaxRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.StateValue;
import it.eng.dome.tmforum.tmf678.v4.model.TaxItem;

public class RevenueBillingMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueBillingMapper.class);
		
	/**
	 * Maps a RevenueBill object to a list of AppliedCustomerBillingRate objects.
	 * This method iterates through the revenue items of the RevenueBill,
	 * and for each item, it recursively collects all leaf-level RevenueItems
	 * to be converted into AppliedCustomerBillingRate objects.
	 * @param revenueBill The RevenueBill object containing the revenue data.
	 * @param subscription The Subscription object related to the billing.
	 * @return A List of AppliedCustomerBillingRate objects, or an empty list if the input RevenueBill is null or has no revenue items.
	*/
	public static List<AppliedCustomerBillingRate> toACBRList(RevenueBill revenueBill, Subscription subscription) {
	    if (revenueBill == null || revenueBill.getRevenueItems() == null || revenueBill.getRevenueItems().isEmpty()) {
	        return Collections.emptyList();
	    }

	    List<AppliedCustomerBillingRate> acbrList = new ArrayList<>();
	    for (RevenueItem item : revenueBill.getRevenueItems()) {
			// For each item, call a recursive helper method to find all "leaf" items and map them to AppliedCustomerBillingRate objects.
	        collectLeafItemsAndMap(item, revenueBill, subscription, acbrList);
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
	 * @param revenueBill The RevenueBill object from which the item originates.
	 * @param subscription The Subscription object related to the billing.
	 * @param acbrList The list to which the generated AppliedCustomerBillingRate objects will be added.
	*/
	private static void collectLeafItemsAndMap(RevenueItem item, RevenueBill revenueBill, Subscription subscription, List<AppliedCustomerBillingRate> acbrList) {
		// Base case for the recursion: if the item is null, simply return.
		if (item == null) return;
		
		// if had a son, iterate
		if (item.getItems() != null && !item.getItems().isEmpty()) {
			for (RevenueItem child : item.getItems()) {
				// if it has children, recursively call this method for each child.
				collectLeafItemsAndMap(child, revenueBill, subscription, acbrList);
			}
		} else {
			// this is a leaf node (no children). Billable item that can be mapped to an ACBR.
			try {
//				if (item.getOverallValue() != null && item.getOverallValue() != 0.0) {
				// map the leaf RevenueItem to an AppliedCustomerBillingRate and add it to the list.
				acbrList.add(toACBR(item, revenueBill, subscription));
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
	 * from the provided RevenueItem, RevenueBill, Subscription, and BillingAccountRef.
	 *
	 * @param item The RevenueItem to be mapped. This is expected to be a "leaf" item from the bill's hierarchy.
	 * @param revenueBill The RevenueBill object from which the RevenueItem and other context (like the billing period) originate.
	 * @param subscription The Subscription related to this billing rate, used to enrich the ACBR's details.
	 * @return A new AppliedCustomerBillingRate object populated with the provided data, or null if the input item is null.
	 * @throws IllegalArgumentException if the RevenueBill or its period are null, as these are mandatory for the ACBR.
	*/
	public static AppliedCustomerBillingRate toACBR(RevenueItem item, RevenueBill revenueBill, Subscription subscription) {
	    if (item == null) {
	    	logger.warn("Cannot map to AppliedCustomerBillingRate: RevenueItem is null");
	    	return null;
	    }
	    
	    if (revenueBill == null || revenueBill.getPeriod() == null) {
	        throw new IllegalArgumentException("RevenueBill or its period must not be null");
	    }

		//id and base data
	    AppliedCustomerBillingRate acbr = new AppliedCustomerBillingRate();
	    acbr.setId("urn:ngsi-ld:applied-customer-billing-rate:" + UUID.randomUUID());
	    acbr.setHref(acbr.getId());
	    acbr.setName("Applied Customer Billing Rate of " + item.getName());
	    acbr.setDescription("Applied Customer Billing Rate of " 
	        + (subscription != null ? subscription.getName() : "") 
	        + " for period " + revenueBill.getPeriod().getStartDateTime() + " - " + revenueBill.getPeriod().getEndDateTime());
	    acbr.setDate(revenueBill.getBillTime());
	    acbr.setIsBilled(false); 
	    acbr.setType(item.getType());
	    acbr.setPeriodCoverage(revenueBill.getPeriod());

		//ref
		acbr.setRelatedParty(revenueBill.getRelatedParties());
		
		acbr.setBill(null); //from invoice

	    acbr.setProduct(subscription != null ? RevenueProductMapper.toProductRef(subscription) : null);
	    
	    acbr.setBillingAccount(null); // set after

	    if (item.getOverallValue() != null) {
	        Money money = new Money();
	        money.setValue(item.getOverallValue().floatValue());
	        money.setUnit(item.getCurrency());
	        acbr.setTaxExcludedAmount(money);
	    } else {
	        logger.debug("RevenueItem '{}' has no overall value set", item.getName());
	    }

		acbr.appliedTax(null); // from invoice
		acbr.setTaxIncludedAmount(null); //from invoice

	    return acbr;
	}
	
	/*
	 * REVENUE BILL TO CUSTOMER BILL
	 */

	/**
	 * Maps a RevenueBill object to a CustomerBill object, following the TMF678 specification.
	 * This method populates the CustomerBill's fields such as ID, dates, billing period,
	 * amounts, taxes, and related parties based on the provided RevenueBill.
	 *
	 * @param revenueBill The RevenueBill object containing the source data.
	 * @return A new CustomerBill object populated with the mapped data.
	 * @throws IllegalArgumentException if the provided RevenueBill is null.
	*/
	public static CustomerBill toCB(RevenueBill revenueBill) {
		if (revenueBill == null) {
		    logger.error("toCB: RevenueBill is null, cannot map to CustomerBill");
		    throw new IllegalArgumentException("RevenueBill cannot be null");
		}

		CustomerBill cb = new CustomerBill();

        String billId = revenueBill.getId();
        cb.setId(billId.replace("urn:ngsi-ld:revenuebill", "urn:ngsi-ld:customerbill"));
//      cb.setHref(billId);
        cb.setBillDate(revenueBill.getBillTime());
        cb.setLastUpdate(OffsetDateTime.now()); //we can assume that the last update is now

        cb.setBillingPeriod(revenueBill.getPeriod());
//		cb.setCategory("normal"); // we don't know if they are used
        cb.setRunType("onCycle");
        cb.setState(StateValue.NEW);

		// amounts
        Money taxExcludedAmount = new Money();
        taxExcludedAmount.setUnit(revenueBill.getRevenueItems().get(0).getCurrency());
        taxExcludedAmount.setValue(revenueBill.getAmount().floatValue());
        cb.setTaxExcludedAmount(taxExcludedAmount);

		// REF
		cb.setRelatedParty(revenueBill.getRelatedParties());
		cb.setBillingAccount(null); // set after
		cb.setAppliedPayment(new ArrayList<>());
		
//        cb.setFinancialAccount(null);
//        cb.setBillDocument(new ArrayList<>());
//        cb.setPaymentMethod(null);
        return cb;
    }
	
	public static TaxItem toTaxItem(AppliedBillingTaxRate abtr) {
		TaxItem out = new TaxItem();
		out.setTaxCategory(abtr.getTaxCategory());
		out.setTaxRate(abtr.getTaxRate());
		
		return out;
	}
}

