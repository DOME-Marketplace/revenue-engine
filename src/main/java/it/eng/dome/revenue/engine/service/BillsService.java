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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.model.Invoice;
import it.eng.dome.revenue.engine.exception.BadRevenuePlanException;
import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
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
import it.eng.dome.revenue.engine.utils.IdUtils;
import it.eng.dome.revenue.engine.utils.TmfConverter;
import it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.TaxItem;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

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

        // Iterate over bills for that subscription until found
        for (RevenueBill bill : this.getSubscriptionBills(subscriptionId)) {
            if (billId.equals(bill.getId()))
                return bill;
        }

        return null;
    }
    
    /**
     * Retrieves all bills for a given subscription ID.
     * 
     * @param subscriptionId the ID of the subscription for which to retrieve bills
     * @return a list of RevenueBill objects representing the bills for the subscription
     * @throws Exception if an error occurs during retrieval
     */
    public List<RevenueBill> getSubscriptionBills(String subscriptionId) throws Exception {    
        logger.info("Fetch bills for subscription with ID {}", subscriptionId);
        try {
            Set<RevenueBill> bills = new TreeSet<>(new RevenueBillComparator());
            Subscription subscription = this.subscriptionService.getSubscriptionByProductId(subscriptionId);

            Plan plan = planService.getResolvedPlanById(subscription.getPlan().getId(), subscription);
            subscription.setPlan(plan);

            List<RevenueItem> items = this.statementsService.getItemsForSubscription(subscriptionId);
            for (TimePeriod tp : this.statementsService.getBillPeriods(subscriptionId)) {
                RevenueBill bill = new RevenueBill();
                bill.setRelatedParties(subscription.getRelatedParties());
                bill.setSubscriptionId(subscription.getId());
                bill.setPeriod(tp);
                for (RevenueItem item : items) {
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
    
    /**
     * Retrieve a Customer Bill (CB) for a given RevenueBill ID.
     * 
     * @param revenueBillId the ID of the RevenueBill for which to retrieve CB
     * @return a Customer Bill object representing the RevenueBill for TMF
     * @throws Exception if an error occurs during retrieval
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
    
    /**
     * Retrieves Applied Customer Billing Rate (ACBR) for a given RevenueBill ID.
     * 
     * @param revenueBillId the ID of the RevenueBill for which to retrieve ACBR
     * @return a List of ACBR objects representing the bills of RevenueBill for TMF
     * @throws Exception if an error occurs during retrieval
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
     * @throws Exception if an error occurs during conversion
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

        Invoice billingResponse = this.buildAndApplyTaxesForRevenueBill(rb);

        CustomerBill cb = billingResponse.getCustomerBill();
        Subscription sub = subscriptionService.getSubscriptionByProductId(rb.getSubscriptionId());

        try {
            cb.setBillingAccount(TmfConverter.convertBillingAccountRefTo678(
                tmfDataRetriever.retrieveBillingAccountByProductId(rb.getSubscriptionId())));
        } catch (BadTmfDataException e) {
            logger.error("Bad TMF data error setting billing account for subscription ID {}: {}", 
                         rb.getSubscriptionId(), e.getMessage(), e);
            throw e;
        } catch (ExternalServiceException e) {
            logger.error("External service error setting billing account for subscription ID {}: {}", 
                         rb.getSubscriptionId(), e.getMessage(), e);
            throw e;
        }
        
        // Next bill date and payment due date
        Plan resolvedPlan = planService.getResolvedPlanById(sub.getPlan().getId(), sub);
        sub.setPlan(resolvedPlan);
        
        SubscriptionTimeHelper th = new SubscriptionTimeHelper(sub);

        OffsetDateTime billDate = rb.getPeriod().getEndDateTime();
        if (resolvedPlan.getBillCycleSpecification() != null && 
            resolvedPlan.getBillCycleSpecification().getBillingDateShift() != null) {
            billDate = billDate.plusDays(resolvedPlan.getBillCycleSpecification().getBillingDateShift());
        }
        cb.setBillDate(billDate);

        OffsetDateTime nextBillDate = th.rollBillPeriod(billDate, 
            resolvedPlan.getBillCycleSpecification().getBillingPeriodLength());
        cb.setNextBillDate(nextBillDate);

        OffsetDateTime paymentDueDate = billDate;
        if (resolvedPlan.getBillCycleSpecification() != null && 
            resolvedPlan.getBillCycleSpecification().getPaymentDueDateOffset() != null) {
            paymentDueDate = paymentDueDate.plusDays(
                resolvedPlan.getBillCycleSpecification().getPaymentDueDateOffset());
        }
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
     * @throws Exception if an error occurs during processing
     */
    private List<AppliedCustomerBillingRate> getACBRsByRevenueBill(RevenueBill rb) throws Exception {
        if (rb == null) {
            throw new IllegalArgumentException("RevenueBill cannot be null");
        }
        if (rb.getRelatedParties() == null || rb.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in RevenueBill");
        }

        Invoice billingResponse = this.buildAndApplyTaxesForRevenueBill(rb);
        return billingResponse.getAcbrs();
    }

    /**
     * Centralized method that builds CustomerBill and ACBRs from a RevenueBill
     * and applies taxes in one clean step.
     * 
     * @param rb the RevenueBill to process
     * @return BillingResponseDTO containing CustomerBill and ACBRs with applied taxes
     * @throws IllegalArgumentException if RevenueBill is null
     * @throws IllegalStateException if required data cannot be found or processing fails
     * @throws Exception if an error occurs during processing
     */
    public Invoice buildAndApplyTaxesForRevenueBill(RevenueBill rb) throws Exception {
        if (rb == null) {
            throw new IllegalArgumentException("RevenueBill cannot be null");
        }

        Subscription subscription = subscriptionService.getSubscriptionByProductId(rb.getSubscriptionId());
        if (subscription == null) {
            throw new IllegalStateException("Subscription not found for subscriptionId: " + rb.getSubscriptionId());
        }

        Plan resolvedPlan = planService.getResolvedPlanById(subscription.getPlan().getId(), subscription);
        subscription.setPlan(resolvedPlan);

        // Build base structures
        RevenueBillingMapper mapper = new RevenueBillingMapper(subscription, rb);
        CustomerBill cb = mapper.getCustomerBill();
        List<AppliedCustomerBillingRate> acbrList = mapper.generateACBRs();

        // Set references
        acbrList = setBillingAccountRef(acbrList, subscription.getId());
        acbrList = setCustomerBillRef(acbrList, rb);

        // Apply taxes through invoicing service
        Invoice response = this.applyTaxes(cb, acbrList);
        if (response == null) {
            throw new IllegalStateException("applyTaxes returned null response");
        }

        // Consolidate tax items to avoid duplicates
        if (response.getCustomerBill() != null && response.getCustomerBill().getTaxItem() != null) {
            List<TaxItem> consolidatedTaxItems = consolidateTaxItems(
                response.getCustomerBill().getTaxItem()
            );
            response.getCustomerBill().setTaxItem(consolidatedTaxItems);
        }

        return response;
    }

    /**
     * Consolidates tax items by grouping them by tax category and tax rate.
     * Sums tax amounts for each unique combination of category and rate.
     * This ensures that instead of multiple TaxItems with the same category/rate,
     * we have a single consolidated TaxItem with the total amount.
     * 
     * According to TMF678 specification: "A tax item is created for each tax rate
     * and tax type used in the bill."
     *
     * @param taxItems List of TaxItem objects to consolidate
     * @return Consolidated list of TaxItem objects with unique category/rate combinations
     */
    private List<TaxItem> consolidateTaxItems(List<TaxItem> taxItems) {
        if (taxItems == null || taxItems.isEmpty()) {
            return taxItems;
        }
        
        // Group tax items by category and rate
        Map<String, Map<Float, TaxItem>> taxMap = new HashMap<>();
        
        for (TaxItem item : taxItems) {
            String category = item.getTaxCategory();
            Float rate = item.getTaxRate();
            
            // Skip null items
            if (category == null || rate == null) {
                continue;
            }
            
            taxMap.putIfAbsent(category, new HashMap<>());
            
            if (taxMap.get(category).containsKey(rate)) {
                // If exists, sum the amounts
                TaxItem existingItem = taxMap.get(category).get(rate);
                Money existingAmount = existingItem.getTaxAmount();
                Money newAmount = item.getTaxAmount();
                
                // Sum amounts (ensure currencies are the same)
                if (existingAmount != null && newAmount != null && 
                    existingAmount.getUnit() != null && newAmount.getUnit() != null &&
                    existingAmount.getUnit().equals(newAmount.getUnit())) {
                    Float existingValue = existingAmount.getValue() != null ? existingAmount.getValue() : 0.0f;
                    Float newValue = newAmount.getValue() != null ? newAmount.getValue() : 0.0f;
                    existingAmount.setValue(existingValue + newValue);
                }
            } else {
                // Otherwise, add the new item
                taxMap.get(category).put(rate, item);
            }
        }
        
        // Extract all grouped items
        List<TaxItem> consolidatedItems = new ArrayList<>();
        for (Map<Float, TaxItem> rateMap : taxMap.values()) {
            consolidatedItems.addAll(rateMap.values());
        }
        
        // Filter out items with zero amount (optional)
        consolidatedItems.removeIf(item -> 
            item.getTaxAmount() != null && 
            item.getTaxAmount().getValue() != null && 
            Math.abs(item.getTaxAmount().getValue()) < 0.001f // Threshold for negligible amounts
        );
        
        return consolidatedItems;
    }


    /** 
     * Apply Taxes on a CustomerBill and list of ACBRs.
     * 
     * @param customerBill CustomerBill to apply taxes to
     * @param acbrs List of ACBRs to apply taxes to
     * @return BillingResponseDTO containing CustomerBill and ACBRs with applied taxes
     * @throws Exception if an error occurs during tax application
     */
    private Invoice applyTaxes(CustomerBill customerBill, List<AppliedCustomerBillingRate> acbrs) throws Exception {
        if (customerBill == null) {
            logger.info("No customer bill received, no taxes to apply");
            return null;
        } else {
            return this.invoicingService.applyTaxes(customerBill, acbrs);
        }
    }
    

    /**
     * Set Customer Bill Reference for each object in a List of Applied Customer Billing Rate.
     * 
     * @param acbrs List of ACBRs to set customer bill reference for
     * @param rb RevenueBill used to generate a CustomerBill ID
     * @return List of ACBRs with Customer Bill Ref attribute set for each object
     */
    private List<AppliedCustomerBillingRate> setCustomerBillRef(List<AppliedCustomerBillingRate> acbrs, RevenueBill rb) {
        // This ACBR ID is for local instance of ACBR. When we persist the ACBR, we provide another ID.
        BillRef billRef = new BillRef();
        String billId = rb.getId();
        billRef.setId(billId.replace("urn:ngsi-ld:revenuebill", "urn:ngsi-ld:customerbill"));
        
        for (AppliedCustomerBillingRate acbr : acbrs) {
            acbr.setBill(billRef);
        }
        
        return acbrs;
    }
    
    /**
     * Set Billing Account Reference for each object in a List of Applied Customer Billing Rate.
     * 
     * @param acbrs List of ACBRs to set billing account reference for
     * @param subscriptionId Subscription ID used to retrieve Billing Account
     * @return List of ACBRs with Billing Account Ref attribute set for each object
     * @throws BadTmfDataException if TMF data retrieval fails
     * @throws ExternalServiceException if external service call fails
     */
    private List<AppliedCustomerBillingRate> setBillingAccountRef(List<AppliedCustomerBillingRate> acbrs, 
                                                                  String subscriptionId) 
            throws BadTmfDataException, ExternalServiceException {
        BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByProductId(subscriptionId);
        if (billingAccountRef == null) {
            logger.warn("BillingAccountRef is null for subscription ID {}, ACBRs will have null billingAccount", 
                       subscriptionId);
        }
        
        for (AppliedCustomerBillingRate acbr : acbrs) {
            acbr.setBillingAccount(TmfConverter.convertBillingAccountRefTo678(billingAccountRef));
        }
        
        return acbrs;
    }
}