package it.eng.dome.revenue.engine.mapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.StateValue;

public class RevenueBillingMapper {
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueBillingMapper.class);
	
	/*
	 * ITEMS OF SIMPLE BILL
	 *  TO ABCR
	 */
	
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
	    acbr.setId("urn:ngsi-ld:applied-customer-billing-rate:" + UUID.randomUUID().toString());
	    acbr.setHref(acbr.getId());
	    acbr.setName("Applied Customer Billing Rate of " + item.getName());
	    acbr.setDescription("Applied Customer Billing Rate of " 
	        + (subscription != null ? subscription.getName() : "") 
	        + " for period " + sb.getPeriod().getStartDateTime() + " - " + sb.getPeriod().getEndDateTime());
	    acbr.setDate(sb.getBillTime());
	    acbr.setIsBilled(false); // can we assume that it is false at start?
	    acbr.setType(item.getType());
	    acbr.setPeriodCoverage(sb.getPeriod());

		//ref
		acbr.setRelatedParty(sb.getRelatedParties());
		
		acbr.setBill(null);

	    acbr.setProduct(subscription != null ? RevenueProductMapper.toProductRef(subscription) : null);
	    
	    acbr.setBillingAccount(billingAccountRef);

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

	// Maps a RevenueStatement to an AppliedCustomerBillingRate (ACBR) object.
	/* 
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

        return acbr;
    }
*/
	
	/*
	 * SIMPLE BILL TO CUSTOMER BILL
	 */

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

        String billId = simpleBill.getId();
        cb.setId(billId.replace("urn:ngsi-ld:simplebill", "urn:ngsi-ld:customerbill"));
//      cb.setHref(billId);
//      cb.setBillNo(billId.substring(billId.lastIndexOf(":") + 1, billId.length()).substring(0, 6)); // not currently in use. Ask stefania more info
        cb.setBillDate(simpleBill.getBillTime());
        cb.setLastUpdate(OffsetDateTime.now()); //we can assume that the last update is now
//        cb.setNextBillDate(simpleBill.getPeriod().getEndDateTime().plusMonths(1)); //?
//        cb.setPaymentDueDate(simpleBill.getPeriod().getEndDateTime().plusDays(10)); // Q: How many days after the invoice date should we set the due date?
        cb.setBillingPeriod(simpleBill.getPeriod());
//		cb.setCategory("normal"); // we don't know if they are used
//      cb.setRunType("onCycle"); // onCycle or offCycle? we don't know if they are used
        cb.setState(StateValue.NEW);

		// amounts
        Money taxIncludedAmount = new Money();
        taxIncludedAmount.setUnit("EUR");
        taxIncludedAmount.setValue(simpleBill.getAmount().floatValue());
        cb.taxIncludedAmount(taxIncludedAmount);
//		Float baseAmount = simpleBill.getAmount().floatValue();
//		BillingAmountCalculator.applyAmounts(cb, baseAmount);

		// REF
		cb.setRelatedParty(simpleBill.getRelatedParties());
		cb.setBillingAccount(billingAccountRef);
		cb.setAppliedPayment(new ArrayList<>());
		
//        cb.setFinancialAccount(null);
//        cb.setBillDocument(new ArrayList<>());
//        cb.setPaymentMethod(null);
        return cb;
    }
}

