package it.eng.dome.revenue.engine.service;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.exception.BadRevenuePlanException;
import it.eng.dome.revenue.engine.invoicing.InvoicingService;
import it.eng.dome.revenue.engine.mapper.RevenueBillingMapper;
import it.eng.dome.revenue.engine.model.*;
import it.eng.dome.revenue.engine.model.comparator.RevenueBillComparator;
import it.eng.dome.revenue.engine.service.cached.CachedPlanService;
import it.eng.dome.revenue.engine.service.cached.CachedStatementsService;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.utils.IdUtils;
import it.eng.dome.revenue.engine.utils.TmfConverter;
import it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class BillsService {
	
	private final Logger logger = LoggerFactory.getLogger(BillsService.class);

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

    /**
	 * Retrieves a bill by its ID.
	 * 
	 * @param billId the ID of the bill to retrieve
	 * @return the RevenueBill object if found, null otherwise
	 * @throws Exception if an error occurs during retrieval
	*/
    public RevenueBill getRevenueBillById(String billId) throws Exception {
    	logger.info("Fetch bill with ID {}", billId);

        String[] parts = IdUtils.unpack(billId, "revenuebill");
        String subscriptionId = parts[0];

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

            Plan plan = planService.getResolvedPlanById(subscription.getPlan().getId(),subscription);
	        subscription.setPlan(plan);

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
        } catch (BadRevenuePlanException e) {
            logger.error("Bad revenue plan error retrieving subscription bills for ID {}: {}", subscriptionId, e.getMessage(), e);
            throw e;
        } catch (BadTmfDataException e) {
            logger.error("Bad TMF data error retrieving subscription bills for ID {}: {}", subscriptionId, e.getMessage(), e);
            throw e;
        } catch (ExternalServiceException e) {
            logger.error("External service error retrieving subscription bills for ID {}: {}", subscriptionId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
        	logger.error("Error retrieving subscription bills for ID {}: {}", subscriptionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /** Retrieve a Customer Bill (CB) for a given RevenueBill ID.
	 * 
	 * @param revenueBillId the ID of the RevenueBill for which to retrieve CB
	 * @return a Customer Bill objects representing the RevenueBill for tmf
	*/
    public CustomerBill getCustomerBillByRevenueBillId(String revenueBillId) throws Exception {
    	RevenueBill rb = new RevenueBill();
		try {
			rb = this.getRevenueBillById(revenueBillId);
		} catch (BadRevenuePlanException e) {
			logger.error("Bad revenue plan error retrieving Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
			throw e;
		} catch (BadTmfDataException e) {
			logger.error("Bad TMF data error retrieving Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
			throw e;
		} catch (ExternalServiceException e) {
			logger.error("External service error retrieving Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			logger.error("Failed to retrieve Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
			throw e;
		}
        return this.getCustomerBillByRevenueBill(rb);
    }
    
    /** Retrieves Applied Customer Billing Rate (ACBR) for a given RevenueBill ID.
 	 * 
 	 * @param revenueBillId the ID of the RevenueBill for which to retrieve ACBR
 	 * @return a List of ACBR objects representing the bills of RevenueBill for tmf
 	*/
    public List<AppliedCustomerBillingRate> getACBRsByRevenueBillId(String revenueBillId) throws Exception {
    	RevenueBill rb = new RevenueBill();
		try {
			rb = this.getRevenueBillById(revenueBillId);
		} catch (BadRevenuePlanException e) {
			logger.error("Bad revenue plan error retrieving Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
			throw e;
		} catch (BadTmfDataException e) {
			logger.error("Bad TMF data error retrieving Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
			throw e;
		} catch (ExternalServiceException e) {
			logger.error("External service error retrieving Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			logger.error("Failed to retrieve Revenue Bill with ID {}: {}", revenueBillId, e.getMessage(), e);
			throw e;
		}

        return this.getACBRsByRevenueBill(rb);
    }
    
    /**
	 * Builds a CustomerBill from a RevenueBill.
	 * 
	 * @param rb the RevenueBill to convert
	 * @return a CustomerBill object
	 * @throws IllegalArgumentException if the RevenueBill is null or does not contain related party information
	 */
    public CustomerBill getCustomerBillByRevenueBill(RevenueBill rb) throws Exception {

    	if (rb == null) {
            throw new IllegalArgumentException("RevenueBill cannot be null");
        }
        if (rb.getRelatedParties() == null || rb.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in RevenueBill");
        }
        if (rb.getPeriod() == null || rb.getPeriod().getEndDateTime() == null) {
            throw new IllegalArgumentException("RevenueBill period or endDateTime is missing");
        }
        
        CustomerBill cb = RevenueBillingMapper.toCB(rb);

		try {
			cb.setBillingAccount(TmfConverter.convertBillingAccountRefTo678(tmfDataRetriever.retrieveBillingAccountByProductId(rb.getSubscriptionId())));
		} catch (BadTmfDataException e) {
			logger.error("Bad TMF data error setting billing account for subscription ID {}: {}", rb.getSubscriptionId(), e.getMessage(), e);
			throw e;
		} catch (ExternalServiceException e) {
			logger.error("External service error setting billing account for subscription ID {}: {}", rb.getSubscriptionId(), e.getMessage(), e);
			throw e;
		}
        
		// Next bill date and payment due date
		Subscription sub = subscriptionService.getSubscriptionByProductId(rb.getSubscriptionId());
        /*
		Plan plan = planService.getPlanById(sub.getPlan().getId());
		PlanResolver planResolver = new PlanResolver(sub);
		Plan resolvedPlan = planResolver.resolve(plan);
        */
        Plan resolvedPlan = planService.getResolvedPlanById(sub.getPlan().getId(), sub);
		sub.setPlan(resolvedPlan);
		
		SubscriptionTimeHelper th = new SubscriptionTimeHelper(sub);

        OffsetDateTime billDate = rb.getPeriod().getEndDateTime();
        if(resolvedPlan.getBillCycleSpecification()!=null && resolvedPlan.getBillCycleSpecification().getBillingDateShift()!=null)
            billDate = billDate.plusDays(resolvedPlan.getBillCycleSpecification().getBillingDateShift());
        cb.setBillDate(billDate);

		OffsetDateTime nextBillDate = th.rollBillPeriod(billDate, resolvedPlan.getBillCycleSpecification().getBillingPeriodLength());
		cb.setNextBillDate(nextBillDate);

		OffsetDateTime paymentDueDate = billDate;
        if(resolvedPlan.getBillCycleSpecification()!=null && resolvedPlan.getBillCycleSpecification().getPaymentDueDateOffset()!=null)
            paymentDueDate = paymentDueDate.plusDays(resolvedPlan.getBillCycleSpecification().getPaymentDueDateOffset());
		cb.setPaymentDueDate(paymentDueDate);
		
		//apply tax
		List<AppliedCustomerBillingRate> acbrs = this.getACBRsByRevenueBill(rb);
	
		List<TaxItem> taxItems = this.getTaxItemListFromACBRs(acbrs);
    	
    	cb.setTaxItem(taxItems);
        
    	//amounts
    	Money taxIncludedTaxes = this.computeTaxIncludedAmount(acbrs);
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

                if (newItem.getTaxCategory() == null || newItem.getTaxRate() == null) {
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
        if (a.getValue() == null || b.getValue() == null) {
            throw new IllegalArgumentException("Cannot sum Money: one of the values is null");
        }
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
        if(taxItem.getTaxRate() == null) {
        	throw new IllegalArgumentException("AppliedBillingTaxRate must have a non-null taxRate");
        }
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
            logger.debug("empty or null acbr list. Returning amount of zero");
//            throw new IllegalArgumentException("AppliedCustomerBillingRate list cannot be null or empty");
            return new Money().value(0f);
        }
    	
    	float sum = 0.0f;
    	String unit = null;
    	
    	for (AppliedCustomerBillingRate acbr : acbrs) {
            if(acbr.getTaxIncludedAmount() == null || acbr.getTaxIncludedAmount().getValue() == null) {
                	throw new IllegalArgumentException("Each AppliedCustomerBillingRate must have a non-null taxIncludedAmount with a value");
            }
            sum = sum + acbr.getTaxIncludedAmount().getValue();
    		if(unit == null || !unit.equals(acbr.getTaxIncludedAmount().getUnit())) {
    			unit = acbr.getTaxIncludedAmount().getUnit();
    		}
		}
    	
    	Money m = new Money();
    	m.setUnit(unit);
    	m.setValue(sum);
    	
    	return m;
    }
    
    /**
     * Retrieves the list of AppliedCustomerBillingRate for a given RevenueBill.
     * Performs mapping from RevenueBill and Subscription, sets billing account reference,
     * customer bill reference, and applies taxes.
     *
     * @param rb RevenueBill object (must not be null and must have related parties)
     * @return List of AppliedCustomerBillingRate
     * @throws IllegalArgumentException if the RevenueBill or its related parties are null/empty
     * @throws IllegalStateException if required data (Subscription, Buyer party, etc.) cannot be retrieved
     */
    private List<AppliedCustomerBillingRate> getACBRsByRevenueBill(RevenueBill rb) throws Exception {
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
        
		Plan plan = planService.getPlanById(subscription.getPlan().getId());
		PlanResolver planResolver = new PlanResolver(subscription);
		Plan resolvedPlan = planResolver.resolve(plan);
		subscription.setPlan(resolvedPlan);
        
		List<AppliedCustomerBillingRate> acbrList = RevenueBillingMapper.toACBRList(rb, subscription);
        if (acbrList.isEmpty()) {
//            throw new IllegalStateException("Failed to map RevenueBill and Subscription to AppliedCustomerBillingRate list");
        }
        
        for (AppliedCustomerBillingRate acbr : acbrList) {
			acbr.setRelatedParty(rb.getRelatedParties());
		}
        
        acbrList = this.setBillingAccountRef(acbrList, subscription.getId());
//        if (acbrList == null || acbrList.isEmpty()) {
//            throw new IllegalStateException("Failed to set billing account reference on AppliedCustomerBillingRate list");
//        }
        
        acbrList = this.setCustomerBillRef(acbrList, rb);
//        if (acbrList == null || acbrList.isEmpty()) {
//            throw new IllegalStateException("Failed to set customer bill reference on AppliedCustomerBillingRate list");
//        }
        
        acbrList = this.applyTaxes(acbrList);
//        if (acbrList == null) {
//            throw new IllegalStateException("Failed to apply taxes to AppliedCustomerBillingRate list");
//        }
        
        return acbrList;
    }

    /** Apply Taxes on a list of ACBR
 	 * 
 	 * @param acbrs is a list of ACBR
 	 * @return a list of ACBR with AppliedBillingTaxRate attribute for each object.
 	*/
    private List<AppliedCustomerBillingRate> applyTaxes(List<AppliedCustomerBillingRate> acbrs) throws Exception {

        if(acbrs==null || acbrs.isEmpty()) {
            logger.info("no acbrs received, no taxes to apply");
            return acbrs;
        }
        else {
            // first, retrieve the product
            String productId = acbrs.get(0).getProduct().getId();
            Product product = tmfDataRetriever.getProductById(productId, null);

            // invoke the invoicing servi
            return this.invoicingService.applyTaxees(product, acbrs);
        }

    }
    
    /** Set Customer Bill Ref for each object in a List of Applied Customer Billing Rate
 	 * 
 	 * @param acbrs is a list of ACBR
 	 * @param rb is a RevenueBill used to generate a CustomerBill id
 	 * @return a list of ACBR with Customer Bill Ref attribute for each object.
 	*/
    private List<AppliedCustomerBillingRate> setCustomerBillRef(List<AppliedCustomerBillingRate> acbrs, RevenueBill rb){
		// this acbr id is for local istance of acbr. When we persist the acbr, we provide another id.
		BillRef billRef = new BillRef();
		String billId = rb.getId();
		billRef.setId(billId.replace("urn:ngsi-ld:revenuebill", "urn:ngsi-ld:customerbill"));
		
		for (AppliedCustomerBillingRate acbr : acbrs) {
			acbr.setBill(billRef);
		}
		
		return acbrs;
    }
    
    /** Set Billing Account Ref for each object in a List of Applied Customer Billing Rate
 	 * 
 	 * @param acbrs is a list of ACBR
 	 * @param subscriptionId is a Subscription ID used to retrieve Billing Account
 	 * @return a list of ACBR with Billing Account Ref attribute for each object.
     * @throws ExternalServiceException 
     * @throws BadTmfDataException 
 	*/
  private List<AppliedCustomerBillingRate> setBillingAccountRef(List<AppliedCustomerBillingRate> acbrs, String subscriptionId) throws BadTmfDataException, ExternalServiceException {
	  BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByProductId(subscriptionId);
	  if (billingAccountRef == null) {
		  logger.warn("toCB: billingAccountRef is null, CustomerBill will have null billingAccount");
	  }
	  
	  for (AppliedCustomerBillingRate acbr : acbrs) {
		  acbr.setBillingAccount(TmfConverter.convertBillingAccountRefTo678(billingAccountRef));
	  }
	  
	  return acbrs;
  }
}