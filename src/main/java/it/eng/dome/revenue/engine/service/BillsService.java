package it.eng.dome.revenue.engine.service;

import it.eng.dome.brokerage.billing.dto.BillingResponseDTO;
import it.eng.dome.revenue.engine.exception.BadRevenuePlanException;
import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
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
import it.eng.dome.tmforum.tmf678.v4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
    	RevenueBill rb;
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
    	RevenueBill rb;
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

        BillingResponseDTO billingResponse = this.buildAndApplyTaxesForRevenueBill(rb);

        CustomerBill cb = billingResponse.getCustomerBill();
        Subscription sub = subscriptionService.getSubscriptionByProductId(rb.getSubscriptionId());

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

        return cb;
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

        BillingResponseDTO billingResponse = this.buildAndApplyTaxesForRevenueBill(rb);
        return billingResponse.getAcbr();
    }

    /**
     * Centralized method that builds CustomerBill and ACBRs from a RevenueBill
     * and applies taxes in one clean step.
     */
    public BillingResponseDTO buildAndApplyTaxesForRevenueBill(RevenueBill rb) throws Exception {
        if (rb == null) throw new IllegalArgumentException("RevenueBill cannot be null");

        Subscription subscription = subscriptionService.getSubscriptionByProductId(rb.getSubscriptionId());
        if (subscription == null)
            throw new IllegalStateException("Subscription not found for subscriptionId: " + rb.getSubscriptionId());

        Plan resolvedPlan = planService.getResolvedPlanById(subscription.getPlan().getId(), subscription);
        subscription.setPlan(resolvedPlan);

        // Build base structures
        RevenueBillingMapper mapper = new RevenueBillingMapper(subscription, rb);
        CustomerBill cb = mapper.getCustomerBill();
        List<AppliedCustomerBillingRate> acbrList = mapper.generateACBRs();

        // Set references
        acbrList = setRelatedParties(acbrList, rb);
        acbrList = setBillingAccountRef(acbrList, subscription.getId());
        acbrList = setCustomerBillRef(acbrList, rb);

        // Apply taxes through invoicing service
        BillingResponseDTO response = this.applyTaxes(cb, acbrList);
        if (response == null) {
            throw new IllegalStateException("applyTaxes returned null response");
        }

        return response;
    }

    /** Apply Taxes on a list of ACBR
 	 * 
 	 * @param acbrs is a list of ACBR
 	 * @return a list of ACBR with AppliedBillingTaxRate attribute for each object.
 	*/
    private BillingResponseDTO applyTaxes(CustomerBill customerBill, List<AppliedCustomerBillingRate> acbrs) throws Exception {
        if (customerBill == null) {
            logger.info("no customer bill received, no taxes to apply");
            return null;
        }
        else {
            return this.invoicingService.applyTaxes(customerBill, acbrs);
        }
    }

    /** Set Related Parties ref for each object in a List of Applied Customer Billing Rate
     *
     * @param acbrs is a list of ACBR
     * @param rb is a RevenueBill used to generate a CustomerBill id
     * @return a list of ACBR with Customer Bill Ref attribute for each object.
     */
    private List<AppliedCustomerBillingRate> setRelatedParties(List<AppliedCustomerBillingRate> acbrs, RevenueBill rb) {
        List<RelatedParty> relatedParties = rb.getRelatedParties();

        if (relatedParties == null || relatedParties.isEmpty()) {
            logger.warn("No relatedParties found for RevenueBill {}", rb.getId());
            return acbrs;
        }

        for (AppliedCustomerBillingRate acbr : acbrs) {
            acbr.setRelatedParty(relatedParties);
        }

        return acbrs;
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