package it.eng.dome.revenue.engine.mapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
	
	private static final String STANDARD_CURRENCY_FOR_EMPTY_CB = "EUR";
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueBillingMapper.class);
		
	private Subscription subscription;
	private RevenueBill revenueBill;

	public RevenueBillingMapper() {
	}

	public RevenueBillingMapper(Subscription subscription) {
		this();
		this.subscription = subscription;
	}

	public RevenueBillingMapper(Subscription subscription, RevenueBill revenueBill) {
		this(subscription);
		this.revenueBill = revenueBill;
		prefixNames(this.revenueBill);
	}

	/**
	 * Maps a RevenueBill object to a list of AppliedCustomerBillingRate objects.
	 * This method iterates through the revenue items of the RevenueBill,
	 * and for each item, it recursively collects all leaf-level RevenueItems
	 * to be converted into AppliedCustomerBillingRate objects.
	 * @return A List of AppliedCustomerBillingRate objects, or an empty list if the input RevenueBill is null or has no revenue items.
	*/
	public List<AppliedCustomerBillingRate> generateACBRs() {

	    List<AppliedCustomerBillingRate> acbrs = new ArrayList<>();

		if (this.revenueBill != null || this.revenueBill.getRevenueItems() != null) {
			for (RevenueItem childRevenueItem : revenueBill.getRevenueItems()) {
				acbrs.addAll(this.generateACBRs(childRevenueItem));
			}
		}

	    return acbrs;
	}
	
	/**
	 * Recursively traverses the hierarchy of a RevenueItem to find all "leaf" nodes.
	 * A leaf node is a RevenueItem that does not contain any child items.
	 * Once a leaf node is found, it is mapped to an AppliedCustomerBillingRate object
	 * and added to the provided list.
	 *
	 * @param revenueItem The current RevenueItem in the traversal.
	*/
	private List<AppliedCustomerBillingRate> generateACBRs(RevenueItem revenueItem) {

	    List<AppliedCustomerBillingRate> acbrs = new ArrayList<>();

		if (revenueItem != null) {

			// generate an acbr for this revenueItem (only if leaf or not zero)
			if(revenueItem.isLeaf() || (revenueItem.getValue()!=null && revenueItem.getValue()>0)) {
				if(revenueItem.getValue()!=null) {
					AppliedCustomerBillingRate acbr = this.generateAppliedCustomerBillingRate(revenueItem);
					if(acbr!=null)
						acbrs.add(acbr);
				}
			}
			// for non-leaf nodes, iterate over chidlren
			if (revenueItem.getItems() != null) {
				for (RevenueItem childRevenueItem : revenueItem.getItems()) {
					acbrs.addAll(this.generateACBRs(childRevenueItem));
				}
			} 

		}

		return acbrs;

	}

	/**
	 * Maps a single RevenueItem to an AppliedCustomerBillingRate (ACBR) object.
	 * This method creates a new ACBR instance and populates its fields with data
	 * from the provided RevenueItem, RevenueBill, Subscription, and BillingAccountRef.
	 *
	 * @param item The RevenueItem to be mapped. This is expected to be a "leaf" item from the bill's hierarchy.
	 * @return A new AppliedCustomerBillingRate object populated with the provided data, or null if the input item is null.
	 * @throws IllegalArgumentException if the RevenueBill or its period are null, as these are mandatory for the ACBR.
	*/
	private AppliedCustomerBillingRate generateAppliedCustomerBillingRate(RevenueItem item) {

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
	    acbr.setName(item.getName());
	    acbr.setDescription((subscription != null ? subscription.getName() : "") 
	        + " for period " + revenueBill.getPeriod().getStartDateTime() + " - " + revenueBill.getPeriod().getEndDateTime());
	    acbr.setDate(revenueBill.getPeriod().getEndDateTime());	// from specs, date is the acbr creation date. So, review this.
	    acbr.setIsBilled(false); 
	    acbr.setType(item.getType());
	    acbr.setPeriodCoverage(revenueBill.getPeriod());

		//ref
		acbr.setRelatedParty(revenueBill.getRelatedParties());
		
		acbr.setBill(null); //from invoice

	    acbr.setProduct(subscription != null ? RevenueProductMapper.toProductRef(subscription) : null);
	    
	    acbr.setBillingAccount(null); // set after

	    if (item.getValue() != null) {
	        Money money = new Money();
	        money.setValue(item.getValue().floatValue());
	        money.setUnit(item.getCurrency());
	        acbr.setTaxExcludedAmount(money);
	    } else {
	        logger.debug("RevenueItem '{}' has no value set", item.getName());
	    }

		acbr.appliedTax(null); // from invoice
		acbr.setTaxIncludedAmount(acbr.getTaxExcludedAmount()); //from invoice

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
	public CustomerBill getCustomerBill() {
		if (revenueBill == null) {
		    logger.error("toCB: RevenueBill is null, cannot map to CustomerBill");
		    throw new IllegalArgumentException("RevenueBill cannot be null");
		}

		CustomerBill cb = new CustomerBill();

        String billId = revenueBill.getId();
        cb.setId(billId.replace("urn:ngsi-ld:revenuebill", "urn:ngsi-ld:customerbill"));
//      cb.setHref(billId);
//        cb.setBillDate(revenueBill.getBillTime());
        cb.setLastUpdate(OffsetDateTime.now()); //we can assume that the last update is now

        cb.setBillingPeriod(revenueBill.getPeriod());
//		cb.setCategory("normal"); // we don't know if they are used
        cb.setRunType("onCycle");
        cb.setState(StateValue.NEW);

		// amounts
        Money taxExcludedAmount = new Money();
        if (revenueBill.getRevenueItems() != null && !revenueBill.getRevenueItems().isEmpty()) {
            taxExcludedAmount.setUnit(revenueBill.getRevenueItems().get(0).getCurrency());
        } else {
            // Default currency if no revenue items
            taxExcludedAmount.setUnit(STANDARD_CURRENCY_FOR_EMPTY_CB);
            logger.warn("No revenue items found for bill {}, using default currency", billId);
        }
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
	
	public TaxItem getTaxItem(AppliedBillingTaxRate abtr) {
		TaxItem out = new TaxItem();
		out.setTaxCategory(abtr.getTaxCategory());
		out.setTaxRate(abtr.getTaxRate());		
		return out;
	}

	private static RevenueBill prefixNames(RevenueBill revenueBill) {
		if(revenueBill!=null) {
			if(revenueBill.getRevenueItems()!=null) {
				for(RevenueItem item: revenueBill.getRevenueItems()) {
					prefixNames(item, null);
				}
			}
		}
		
		return revenueBill;
	}

	private static RevenueItem prefixNames(RevenueItem revenueItem, String prefix) {
		if(revenueItem!=null) {
			if(prefix!=null)
				revenueItem.setName(prefix + " / " + revenueItem.getName());
			if(revenueItem.getItems()!=null) {
				for(RevenueItem item: revenueItem.getItems()) {
					prefixNames(item, revenueItem.getName());
				}
			}
		}
		return revenueItem;
	}

}

