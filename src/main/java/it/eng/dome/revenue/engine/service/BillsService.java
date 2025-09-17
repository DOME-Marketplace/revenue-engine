package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.ProductApis;
import it.eng.dome.revenue.engine.invoicing.InvoicingService;
import it.eng.dome.revenue.engine.mapper.RevenueBillingMapper;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.RevenueBill;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.model.comparator.RevenueBillComparator;
import it.eng.dome.revenue.engine.service.cached.CachedPlanService;
import it.eng.dome.revenue.engine.service.cached.CachedStatementsService;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.revenue.engine.utils.TmfConverter;
import it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedBillingTaxRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.TaxItem;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class BillsService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(BillsService.class);

    @Autowired
    private TmfApiFactory tmfApiFactory;

    @Autowired
	private CachedStatementsService statementsService;

    @Autowired
	private CachedSubscriptionService subscriptionService;
    
    @Autowired
	private CachedPlanService planService;
    
    @Autowired
	private TmfCachedDataRetriever tmfDataRetriever;

    @Autowired
	private InvoicingService invoicingService;

    private ProductApis productApis;
    
    private AppliedCustomerBillRateApis acbrApis;

    @Override
    public void afterPropertiesSet() throws Exception {
        productApis = new ProductApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
        acbrApis = new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
    }

    /**
	 * Retrieves a bill by its ID.
	 * 
	 * @param billId the ID of the bill to retrieve
	 * @return the RevenueBill object if found, null otherwise
	 * @throws Exception if an error occurs during retrieval
	*/
    public RevenueBill getRevenueBillById(String billId) throws Exception {
    	logger.info("Fetch bill with ID {}", billId);
        // FIXME: temporary... until we have proper persistence
        // extract the subscription id
        String subscriptionId = "urn:ngsi-ld:product:"+billId.substring(24, 24+36);

        // iterate over bills for that subscription, until found
        for(RevenueBill bill: this.getSubscriptionBills(subscriptionId)) {
            if(billId.equals(bill.getId()))
                return bill;
        }
        return null;
    }
    
    /** Retrieves all bills for a given subscription ID.
	 * 
	 * @param subscriptionId the ID of the subscription for which to retrieve bills
	 * @return a list of RevenueBill objects representing the bills for the subscription
	 * @throws Exception if an error occurs during retrieval
	*/
    public List<RevenueBill> getSubscriptionBills(String subscriptionId) throws Exception {    
	    logger.info("Fetch bills for subscription with ID{}", subscriptionId);
        try {
            Set<RevenueBill> bills = new TreeSet<>(new RevenueBillComparator());
            Subscription subscription = this.subscriptionService.getSubscriptionByProductId(subscriptionId);
            List<RevenueItem> items = this.statementsService.getItemsForSubscription(subscriptionId);
            for(TimePeriod tp: this.statementsService.getBillPeriods(subscriptionId)) {
                RevenueBill bill = new RevenueBill();
                bill.setRelatedParties(subscription.getRelatedParties());
                bill.setSubscriptionId(subscription.getId());
                bill.setPeriod(tp);
                for(RevenueItem item:items) {
                    bill.addRevenueItem(item);
                }
                bills.add(bill);
            }
            return new ArrayList<>(bills);
        } catch (Exception e) {
        	logger.error("Error retrieving subscription bills for ID {}: {}", subscriptionId, e.getMessage(), e);
            throw e;
        }
    }
    
    public CustomerBill getCustomerBillByRevenueBillId(String revenueBillId) {
    	RevenueBill rb = new RevenueBill();
		try {
			rb = this.getRevenueBillById(revenueBillId);
		} catch (Exception e) {
			logger.error("Failed to retrieve Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
		}
        CustomerBill cb = this.getCustomerBillByRevenueBill(rb);
        return cb;
    }
    
    public List<AppliedCustomerBillingRate> getACBRsByRevenueBillId(String revenueBillId) {
    	RevenueBill rb = new RevenueBill();
		try {
			rb = this.getRevenueBillById(revenueBillId);
		} catch (Exception e) {
			logger.error("Failed to retrieve Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
		}		
		List<AppliedCustomerBillingRate> acbrs = this.getACBRsByRevenueBill(rb);
		
		return acbrs;
    }
    
    /**
	 * Builds a CustomerBill from a RevenueBill.
	 * 
	 * @param sb the RevenueBill to convert
	 * @return a CustomerBill object
	 * @throws IllegalArgumentException if the RevenueBill is null or does not contain related party information
	 */
    public CustomerBill getCustomerBillByRevenueBill(RevenueBill sb) {
    	if (sb == null) {
            throw new IllegalArgumentException("RevenueBill cannot be null");
        }
        if (sb.getRelatedParties() == null || sb.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in RevenueBill");
        }
        if (sb.getPeriod() == null || sb.getPeriod().getEndDateTime() == null) {
            throw new IllegalArgumentException("RevenueBill period or endDateTime is missing");
        }
        
        CustomerBill cb = RevenueBillingMapper.toCB(sb);
        if (cb == null) {
            throw new IllegalStateException("Failed to map RevenueBill to CustomerBill");
        }

		cb.setBillingAccount(TmfConverter.convertBillingAccountRefTo678(tmfDataRetriever.retrieveBillingAccountByProductId(sb.getSubscriptionId())));
        
		// NEXT Bill date and Payment Due Date
		Subscription sub = subscriptionService.getSubscriptionByProductId(sb.getSubscriptionId());
		Plan plan = planService.getPlanById(sub.getPlan().getId());
		SubscriptionTimeHelper th = new SubscriptionTimeHelper(sub);
		
		OffsetDateTime nextBillDate = th.rollBillPeriod(sb.getBillTime(), plan.getBillCycleSpecification().getBillingPeriodLength());
		cb.setNextBillDate(nextBillDate); //?

		OffsetDateTime paymentDueDate = cb.getBillDate().plusDays(plan.getBillCycleSpecification().getPaymentDueDateOffset());
		cb.setPaymentDueDate(paymentDueDate);
		
		//apply tax
		List<AppliedCustomerBillingRate> acbrs = this.getACBRsByRevenueBill(sb);
	
		List<TaxItem> taxItems = this.getTaxItemListFromACBRs(acbrs);
    	
    	cb.setTaxItem(taxItems);
        
    	//amounts
    	Money taxIncludedTaxes = this.computeTaxIncludedAmount(acbrs);
    	if (taxIncludedTaxes == null) {
            throw new IllegalStateException("Failed to compute tax included amount");
        }
        cb.setTaxIncludedAmount(taxIncludedTaxes);
		cb.setAmountDue(taxIncludedTaxes);
		cb.setRemainingAmount(taxIncludedTaxes);
		
        //check null condition
        return cb;
    }
    
    /**
     * Extracts and aggregates TaxItem objects from a list of AppliedCustomerBillingRate.
     * Groups results by (taxCategory + taxRate).
     *
     * @param acbrs list of AppliedCustomerBillingRate
     * @return aggregated list of TaxItem
     * @throws IllegalArgumentException if the input list is null
     */
    private List<TaxItem> getTaxItemListFromACBRs(List<AppliedCustomerBillingRate> acbrs) {
        if (acbrs == null) {
            throw new IllegalArgumentException("Input list of AppliedCustomerBillingRate cannot be null");
        }

        Map<String, TaxItem> taxItemsMap = new HashMap<>();

        for (AppliedCustomerBillingRate acbr : acbrs) {
            if (acbr == null) {
                throw new IllegalArgumentException("AppliedCustomerBillingRate element cannot be null");
            }
            if (acbr.getAppliedTax() == null) {
                continue; // no taxes applied, skip
            }

            for (AppliedBillingTaxRate abtr : acbr.getAppliedTax()) {
                if (abtr == null) {
                    throw new IllegalArgumentException("AppliedBillingTaxRate element cannot be null");
                }

                TaxItem newItem = this.getTaxItemFromABTR(abtr, acbr.getTaxExcludedAmount());

                if (newItem == null || newItem.getTaxCategory() == null || newItem.getTaxRate() == null) {
                    throw new IllegalArgumentException("Generated TaxItem or its key fields cannot be null");
                }

                // Unique key: taxCategory + taxRate
                String key = newItem.getTaxCategory() + "|" + newItem.getTaxRate();

                taxItemsMap.merge(key, newItem, (existing, incoming) -> {
                    existing.setTaxAmount(sumMoney(existing.getTaxAmount(), incoming.getTaxAmount()));
                    return existing;
                });
            }
        }

        return new ArrayList<>(taxItemsMap.values());
    }

    /**
     * Adds two Money objects, ensuring that both are non-null and use the same unit.
     * If either argument is null, an IllegalArgumentException is thrown.
     *
     * @param a first Money
     * @param b second Money
     * @return a new Money instance with the summed value
     * @throws IllegalArgumentException if one of the arguments is null or units differ
     */
    private Money sumMoney(Money a, Money b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Cannot sum Money: one of the arguments is null");
        }

        if (!a.getUnit().equals(b.getUnit())) {
            throw new IllegalArgumentException(
                "Cannot sum Money with different units: " + a.getUnit() + " vs " + b.getUnit()
            );
        }

        Money result = new Money();
        result.setUnit(a.getUnit());
        result.setValue(a.getValue() + b.getValue());
        return result;
    }

    /**
     * Converts an AppliedBillingTaxRate into a TaxItem and calculates its taxAmount.
     *
     * @param abtr AppliedBillingTaxRate object (must not be null)
     * @param taxExcludedAmount Money representing the base amount (must not be null)
     * @return TaxItem with taxCategory, taxRate, and calculated taxAmount
     * @throws IllegalArgumentException if any argument is null or contains invalid data
     */
    private TaxItem getTaxItemFromABTR(AppliedBillingTaxRate abtr, Money taxExcludedAmount) {
    	if (abtr == null) {
            throw new IllegalArgumentException("AppliedBillingTaxRate cannot be null");
        }
        if (taxExcludedAmount == null) {
            throw new IllegalArgumentException("taxExcludedAmount cannot be null");
        }
        if (taxExcludedAmount.getValue() == null || taxExcludedAmount.getUnit() == null) {
            throw new IllegalArgumentException("taxExcludedAmount must have non-null value and unit");
        }
        
        // convert AppliedBillingTaxRate to TaxItem (fills taxCategory and taxRate)
		TaxItem taxItem = RevenueBillingMapper.toTaxItem(abtr);
		
		// calculate tax amount
		Float taxAmount = (taxExcludedAmount.getValue() * taxItem.getTaxRate());
		Money taxAmountMoney = new Money();
        taxAmountMoney.setUnit(taxExcludedAmount.getUnit());
        taxAmountMoney.setValue(taxAmount);
        taxItem.setTaxAmount(taxAmountMoney);
        
        return taxItem;
    }
    
    /**
     * Computes the total tax-included amount from a list of AppliedCustomerBillingRate.
     * Sums the taxIncludedAmount of each rate, assuming all amounts have the same currency/unit.
     *
     * @param acbrs List of AppliedCustomerBillingRate (must not be null or contain null elements)
     * @return Money object representing the total tax-included amount
     * @throws IllegalArgumentException if the list is null, empty, or contains invalid Money objects
     */
    private Money computeTaxIncludedAmount(List<AppliedCustomerBillingRate> acbrs) {
    	if (acbrs == null || acbrs.isEmpty()) {
            throw new IllegalArgumentException("AppliedCustomerBillingRate list cannot be null or empty");
        }
    	
    	Float sum = 0.0f;
    	String unit = null;
    	
    	for (AppliedCustomerBillingRate acbr : acbrs) {
    		sum = sum + acbr.getTaxIncludedAmount().getValue();
    		if(unit == null || !unit.equals(acbr.getTaxIncludedAmount().getUnit())) {
    			unit = acbr.getTaxIncludedAmount().getUnit();
    		}
		}
    	
    	Money m = new Money();
    	m.setUnit(unit);;
    	m.setValue(sum);
    	
    	return m;
    }
    
    /**
     * Retrieves the list of AppliedCustomerBillingRate for a given RevenueBill.
     * Performs mapping from RevenueBill and Subscription, sets billing account reference,
     * customer bill reference, and applies taxes.
     *
     * @param sb RevenueBill object (must not be null and must have related parties)
     * @return List of AppliedCustomerBillingRate
     * @throws IllegalArgumentException if the RevenueBill or its related parties are null/empty
     * @throws IllegalStateException if required data (Subscription, Buyer party, etc.) cannot be retrieved
     */
    public List<AppliedCustomerBillingRate> getACBRsByRevenueBill(RevenueBill rb) {
    	if (rb == null) {
            throw new IllegalArgumentException("RevenueBill cannot be null");
        }
        if (rb.getRelatedParties() == null || rb.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in RevenueBill");
        }
        
        Subscription subscription = subscriptionService.getSubscriptionByProductId(rb.getSubscriptionId());
        if (subscription == null) {
            throw new IllegalStateException("Subscription not found for subscriptionId: " + rb.getSubscriptionId());
        }
        
        List<AppliedCustomerBillingRate> acbrList = RevenueBillingMapper.toACBRList(rb, subscription);
        if (acbrList == null) {
            throw new IllegalStateException("Failed to map RevenueBill and Subscription to AppliedCustomerBillingRate list");
        }
        
        acbrList = this.setBillingAccountRef(acbrList, subscription.getId());
        if (acbrList == null) {
            throw new IllegalStateException("Failed to set billing account reference on AppliedCustomerBillingRate list");
        }
        
        acbrList = this.setCustomerBillRef(acbrList, rb);
        if (acbrList == null) {
            throw new IllegalStateException("Failed to set customer bill reference on AppliedCustomerBillingRate list");
        }
        
        acbrList = this.applyTaxes(acbrList);
        if (acbrList == null) {
            throw new IllegalStateException("Failed to apply taxes to AppliedCustomerBillingRate list");
        }
        
        return acbrList;
    }

    public List<AppliedCustomerBillingRate> applyTaxes(List<AppliedCustomerBillingRate> acbrs) {

        // first, retrieve the product
        String productId = acbrs.get(0).getProduct().getId();
        Product product = productApis.getProduct(productId, null);

        // invoke the invoicing servi
        return this.invoicingService.applyTaxees(product, acbrs);
    }
    
    public List<AppliedCustomerBillingRate> setCustomerBillRef(List<AppliedCustomerBillingRate> acbrs, RevenueBill rb){
		// FIXME: Currently, we don't consider persistence.
		BillRef billRef = new BillRef();
		String billId = rb.getId();
		billRef.setId(billId.replace("urn:ngsi-ld:revenuebill", "urn:ngsi-ld:customerbill"));
		
		for (AppliedCustomerBillingRate acbr : acbrs) {
			acbr.setBill(billRef);
		}
		
		return acbrs;
    }
    
  public List<AppliedCustomerBillingRate> setBillingAccountRef(List<AppliedCustomerBillingRate> acbrs, String subscriptionId){
	  BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByProductId(subscriptionId);
	  if (billingAccountRef == null) {
		  logger.warn("toCB: billingAccountRef is null, CustomerBill will have null billingAccount");
	  }
	  
	  for (AppliedCustomerBillingRate acbr : acbrs) {
		  acbr.setBillingAccount(TmfConverter.convertBillingAccountRefTo678(billingAccountRef));
	  }
	  
	  return acbrs;
  }
  
  /**
   * Retrieves the list of AppliedCustomerBillingRate for a given CustomerBill ID.
   *
   * @param cbId ID of CustomerBill object to retrieve ACBRs.
   * @return List of AppliedCustomerBillingRate
   * @throws IllegalArgumentException if the cbId is null/empty
   */
  public List<AppliedCustomerBillingRate> getACBRsByCustomerBillId(String cbId) {
	if (cbId == null) {
	      throw new IllegalArgumentException("CustomerBill ID cannot be null");
	}
  	
  	logger.info("retrieve acbrs by cust id: {} " + cbId);
  	
  	Map<String, String> filter = new HashMap<>();
	filter.put("bill.id", cbId);
  	List<AppliedCustomerBillingRate> acbrs = acbrApis.getAllAppliedCustomerBillingRates(null, filter);

  	if(acbrs == null || acbrs.isEmpty()) {
  		logger.warn("No AppliedCustomerBillingRate found for CustomerBill ID: {}", cbId);
  		return null;
  	}
      
  	return acbrs;
  }
}
