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

	/*
	 * REVENUE BILL TO ACBR
	 */

	/**
	 * Generates a flat list of {@link AppliedCustomerBillingRate} from the revenue bill.
	 *
	 * @return list of generated ACBRs
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
	 * Recursively generates ACBRs from a revenue item and its children.
	 *
	 * @param revenueItem the root revenue item
	 * @return list of generated ACBRs
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
	 * Builds an {@link AppliedCustomerBillingRate} from a single {@link RevenueItem}.
	 *
	 * @param item the revenue item to convert
	 * @return the generated ACBR or null if invalid
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
	    acbr.setHref("/customerBillManagement/v4/appliedCustomerBillingRate/"+ acbr.getId());
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
	 * Maps a {@link RevenueBill} to a {@link CustomerBill}.
	 *
	 * @return the generated customer bill
	 * @throws IllegalArgumentException if the revenue bill is null
	 */
	public CustomerBill getCustomerBill() {
		if (revenueBill == null) {
		    logger.error("toCB: RevenueBill is null, cannot map to CustomerBill");
		    throw new IllegalArgumentException("RevenueBill cannot be null");
		}

		CustomerBill cb = new CustomerBill();

        String billId = revenueBill.getId();
        cb.setId(billId.replace("urn:ngsi-ld:revenuebill", "urn:ngsi-ld:customerbill"));
		cb.setHref("/customerBillManagement/v4/customerBill/"+ cb.getId());
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
//		cb.setBillingAccount(null); // set after
		cb.setAppliedPayment(new ArrayList<>());
		
//        cb.setFinancialAccount(null);
//        cb.setBillDocument(new ArrayList<>());
//        cb.setPaymentMethod(null);
        return cb;
    }

	/**
	 * Converts an {@link AppliedBillingTaxRate} into a {@link TaxItem}.
	 *
	 * @param abtr the applied billing tax rate
	 * @return a corresponding tax item
	 */
	public TaxItem getTaxItem(AppliedBillingTaxRate abtr) {
		TaxItem out = new TaxItem();
		out.setTaxCategory(abtr.getTaxCategory());
		out.setTaxRate(abtr.getTaxRate());		
		return out;
	}

	/**
	 * Adds hierarchical prefixes to item names within a {@link RevenueBill}.
	 *
	 * @param revenueBill the revenue bill to modify
	 * @return the same revenue bill with prefixed names
	 */
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

	/**
	 * Adds a hierarchical prefix to a single {@link RevenueItem} and its children.
	 *
	 * @param revenueItem the revenue item to modify
	 * @param prefix      the prefix to apply
	 * @return the same revenue item with updated names
	 */
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

