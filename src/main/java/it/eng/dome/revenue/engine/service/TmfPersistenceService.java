package it.eng.dome.revenue.engine.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.APIPartyApis;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.RevenueBill;
import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.utils.RelatedPartyUtils;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;
import it.eng.dome.tmforum.tmf678.v4.model.BillRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBillCreate;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;

/**
 * Service for persisting Revenue Bills to TMF as CustomerBills and AppliedCustomerBillingRates.
 * Handles batch processing, deduplication, and tax application.
 * 
 * DEDUPLICATION LOGIC:
 * A CustomerBill is considered a duplicate if ALL of the following match:
 * - Billing period (start AND end dates)
 * - Product ID (from first ACBR)
 * - BUYER related party
 * - Amount (taxExcludedAmount)
 */
@Service
public class TmfPersistenceService {

    private final Logger logger = LoggerFactory.getLogger(TmfPersistenceService.class);
    
    private static final String SCHEMA_LOCATION = "https://raw.githubusercontent.com/DOME-Marketplace/tmf-api/refs/heads/main/DOME/TrackedShareableEntity.schema.json";
    private static final String WATERMARK = "Created by the Revenue Engine";
    
    // Tolerance for float comparison (to handle floating point precision issues)
    private static final float AMOUNT_TOLERANCE = 0.001f;

    @Autowired 
    private BillsService billService;
    
    @Autowired 
    private CachedSubscriptionService subscriptionService;
    
    @Autowired 
    private TmfCachedDataRetriever tmfDataRetriever;

    @Value("${persistence.monthsBack:12}")
    private int monthsBack;

    private final APIPartyApis apiPartyApis;
    private final CustomerBillApis customerBillApis;
    private final AppliedCustomerBillRateApis appliedCustomerBillRateApis;
    
    private String nextBillNo;

    public TmfPersistenceService(APIPartyApis apiPartyApis, CustomerBillApis customerBillApis,
                                AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
        this.apiPartyApis = apiPartyApis;
        this.customerBillApis = customerBillApis;
        this.appliedCustomerBillRateApis = appliedCustomerBillRateApis;
    }

    // ==================== PUBLIC METHODS ====================

    /**
     * Persists all revenue bills for all organizations.
     * Uses batch processing to fetch organizations and persist bills on the fly.
     * 
     * @return List of created CustomerBills
     */
    public List<CustomerBill> persistAllRevenueBills() throws Exception {
        logger.info("=== START persistAllRevenueBills ===");
        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        
        FetchUtils.fetchByBatch(apiPartyApis::listOrganizations, null, null, 100,
            batch -> batch.forEach(org -> {
                try {
                    logger.info("Processing organization: {}", org.getId());
                    List<CustomerBill> orgBills = persistProviderRevenueBills(org.getId());
                    createdCustomerBills.addAll(orgBills);
                    logger.info("Created {} CBs for org {}", orgBills.size(), org.getId());
                } catch (Exception e) {
                    logger.error("Error processing organization {}: {}", org.getId(), e.getMessage(), e);
                }
            })
        );
        
        logger.info("=== END persistAllRevenueBills: Total {} CBs created ===", createdCustomerBills.size());
        return createdCustomerBills;
    }

    /**
     * Persist all revenue bills for a provider; where needed and applicable.
     * @param providerId the provider id
     */
    public List<CustomerBill> persistProviderRevenueBills(String providerId) throws Exception {
        logger.info("=== START persistProviderRevenueBills for provider {} ===", providerId);
        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        
        List<Subscription> buyerSubscriptions = RelatedPartyUtils.retainSubscriptionsWithParty(
                this.subscriptionService.getAllSubscriptions(), providerId, Role.BUYER, true);
        
        logger.info("Found {} buyer subscriptions for provider {}", buyerSubscriptions.size(), providerId);
        
        for (Subscription sub : buyerSubscriptions) {
            try {
                logger.info("Processing subscription: {}", sub.getId());
                List<CustomerBill> subBills = this.persistSubscriptionRevenueBills(sub.getId());
                createdCustomerBills.addAll(subBills);
                logger.info("Created {} CBs for subscription {}", subBills.size(), sub.getId());
            } catch (Exception e) {
                logger.error("Error processing subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
        
        logger.info("=== END persistProviderRevenueBills for provider {}: Total {} CBs ===", 
            providerId, createdCustomerBills.size());
        return createdCustomerBills;
    }

    /**
     * Persist all revenue bills for a subscription; where needed and applicable.
     * Applies date filtering based on:
     * 1. Not persisting bills with billDate in the future
     * 2. Limiting persistence to bills within the configured monthsBack period
     * 
     * @param subscriptionId the subscription id
     */
    public List<CustomerBill> persistSubscriptionRevenueBills(String subscriptionId) throws Exception {
        logger.info("=== START persistSubscriptionRevenueBills for subscription {} ===", subscriptionId);
        logger.info("Using monthsBack configuration: {} months", monthsBack);
        
        // Initialize bill number sequence if not already done
        if (nextBillNo == null) {
            nextBillNo = computeNextBillNoFromTMF();
            logger.info("Initialized billNo sequence starting from {}", nextBillNo);
        }

        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        
        try {
            // Get current date and calculate cutoff date
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime cutoffDate = now.minusMonths(monthsBack);
            logger.info("Date filtering - Now: {}, Cutoff date ({} months back): {}", 
                now.truncatedTo(ChronoUnit.SECONDS), monthsBack, cutoffDate.truncatedTo(ChronoUnit.SECONDS));

            // Get and filter bills for subscription
            List<RevenueBill> allBills = billService.getSubscriptionBills(subscriptionId);
            logger.info("Found {} total bills for subscription", allBills.size());
            
            List<RevenueBill> billsToProcess = filterBillsByDate(allBills, now, cutoffDate);
            
            // Process each filtered bill
            for (int i = 0; i < billsToProcess.size(); i++) {
                RevenueBill bill = billsToProcess.get(i);
                String billId = bill.getId();
                
                logger.info("\n>>> Processing bill {}/{}: {}", (i + 1), billsToProcess.size(), billId);
                
                try {
                    CustomerBill persisted = persistRevenueBill(billId);
                    if (persisted != null) {
                        createdCustomerBills.add(persisted);
                        logger.info("[{}] Successfully persisted CB: {}", (i + 1), persisted.getId());
                    } else {
                        logger.info("[{}] CB not persisted (skipped or duplicate)", (i + 1));
                    }
                } catch (Exception e) {
                    logger.error("[{}] EXCEPTION in persistRevenueBill: {}", (i + 1), e.getMessage(), e);
                    throw e;
                }
            }
            
            logFinalSummary(allBills.size(), billsToProcess.size(), createdCustomerBills.size());
            
        } catch (Exception e) {
            logger.error("Error in persistSubscriptionRevenueBills: {}", e.getMessage(), e);
            throw e;
        }
        
        logger.info("=== END persistSubscriptionRevenueBills: Created {} CBs ===", createdCustomerBills.size());
        return createdCustomerBills;
    }

    /**
     * Persist a revenue bill; where needed and applicable.
     * 
     * EXIT POINTS:
     * 1. getCustomerBillByRevenueBillId returned null
     * 2. BillDate is in the future
     * 3. persistCustomerBill returned null (duplicate or error)
     * 4. Exception thrown
     * 
     * @param revenueBillId the revenue bill id
     * @return the persisted CustomerBill, or null if skipped/already exists
     */
    public CustomerBill persistRevenueBill(String revenueBillId) throws Exception {
        logger.info(">>> persistRevenueBill called for: {}", revenueBillId);
        
        try {
            // STEP 1: Get the local CustomerBill from RevenueBill
            CustomerBill localCb = billService.getCustomerBillByRevenueBillId(revenueBillId);
            if (localCb == null) {
                logger.warn("EXIT POINT 1: getCustomerBillByRevenueBillId returned null for RevenueBill {}", revenueBillId);
                return null;
            }
            
            // Log detailed info about the local CB for debugging
            logCustomerBillDetails("Local CB created", localCb);

            // STEP 2: Check if billDate is in the future
            OffsetDateTime now = OffsetDateTime.now();
            if (localCb.getBillDate() != null && localCb.getBillDate().isAfter(now)) {
                logger.warn("EXIT POINT 2: BillDate {} is in the future (now: {}), skipping", 
                    localCb.getBillDate().truncatedTo(ChronoUnit.SECONDS), 
                    now.truncatedTo(ChronoUnit.SECONDS));
                return null;
            }
            logger.info("BillDate check PASSED");

            // STEP 3: Persist the CustomerBill (includes deduplication check)
            logger.info("Calling persistCustomerBill...");
            CustomerBill persistedCB = persistCustomerBill(localCb, revenueBillId);

            if (persistedCB == null) {
                logger.warn("EXIT POINT 3: persistCustomerBill returned null (duplicate found in TMF or error)");
                return null;
            }
            
            logger.info("CB persisted successfully with id: {}", persistedCB.getId());

            // STEP 4: Persist associated ACBRs
            persistAssociatedACBRs(revenueBillId, persistedCB);
            
            return persistedCB;
            
        } catch (Exception e) {
            logger.error("EXIT POINT 4: Exception in persistRevenueBill for {}: {}", revenueBillId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Persist a CustomerBill if not already present on TMF. 
     * @param cb the local CustomerBill
     * @param revenueBillId the associated revenue bill id for product comparison
     * @return the persisted CustomerBill, or null if already present or error
     */
    public CustomerBill persistCustomerBill(CustomerBill cb, String revenueBillId) throws Exception {
        logger.info("persistCustomerBill called for RevenueBill {}", revenueBillId);
        
        if (cb == null) {
            logger.warn("CustomerBill is null for RevenueBill {}, skipping", revenueBillId);
            return null;
        }

        // Safety check: don't persist future bills
        OffsetDateTime now = OffsetDateTime.now();
        if (cb.getBillDate() != null && cb.getBillDate().isAfter(now)) {
            logger.info("SKIPPING - BillDate {} is in the future", 
                cb.getBillDate().truncatedTo(ChronoUnit.SECONDS));
            return null;
        }

        // Check if already exists in TMF (deduplication)
        logger.info("Checking for duplicates in TMF...");
        CustomerBill existingCustomerBill = findDuplicateInTMF(cb, revenueBillId);
        
        if (existingCustomerBill != null) {
            logger.info("DUPLICATE FOUND: Local CB matches existing TMF CB with id {}", existingCustomerBill.getId());
            return null;
        }
        
        // No duplicate found - proceed with persistence
        logger.info("No duplicate found, proceeding with persistence");
        CustomerBill cbToPersist = watermark(cb);
        
        cbToPersist.setBillNo(nextBillNo);
        nextBillNo = incrementBillNo(nextBillNo);

        String id = customerBillApis.createCustomerBill(CustomerBillCreate.fromJson(cbToPersist.toJson()));
        logger.info("PERSISTENCE SUCCESS: created CB with id {} and billNo {}", id, cbToPersist.getBillNo());
        
        return tmfDataRetriever.getCustomerBill(id);
    }

    /**
     * Persist an AppliedCustomerBillingRate if not already present on TMF. 
     * @param acbr the local AppliedCustomerBillingRate
     * @throws Exception in case of error
     */
    public void persistAppliedCustomerBillingRate(AppliedCustomerBillingRate acbr) throws Exception {
        logger.debug("persistAppliedCustomerBillingRate called");
        
        AppliedCustomerBillingRate existingACBR = findAcbrDuplicateInTMF(acbr);

        if (existingACBR == null) {
            // Not in TMF → persist it
            logger.debug("ACBR not found in TMF, proceeding with persistence");
            AppliedCustomerBillingRate acbrToPersist = watermark(acbr);
            AppliedCustomerBillingRateCreate acbrc = AppliedCustomerBillingRateCreate.fromJson(acbrToPersist.toJson());
            acbrc.setAtSchemaLocation(new URI(SCHEMA_LOCATION));
            acbrc.setRelatedParty(acbr.getRelatedParty());
            String createdId = appliedCustomerBillRateApis.createAppliedCustomerBillingRate(acbrc);
            logger.info("PERSISTENCE: created ACBR with id {}", createdId);
        } else {
            // Already in TMF → don't persist
            logger.info("ACBR already exists in TMF with id {}", existingACBR.getId());
        }
    }

    // ==================== DEDUPLICATION METHODS ====================

    /**
     * Finds a duplicate CustomerBill in TMF based on:
     * - Same billing period (start AND end dates)
     * - Same product ID (from first ACBR)
     * - Same BUYER related party
     * - Same amount (taxExcludedAmount)
     * 
     * If any required field is null/missing on the local CB, allows persistence (returns null).
     * Uses early-stop fetching: stops as soon as a match is found.
     *
     * @param cb the local CustomerBill to check
     * @param revenueBillId the associated revenue bill ID for product comparison
     * @return the matched CustomerBill from TMF, or null if no duplicate found
     * @throws Exception if any API call fails
     */
    private CustomerBill findDuplicateInTMF(CustomerBill cb, String revenueBillId) throws Exception {
        logger.info("=== findDuplicateInTMF for RevenueBill {} ===", revenueBillId);

        // Extract local comparison values
        LocalCbData localData = extractLocalCbData(cb, revenueBillId);
        if (localData == null) {
            logger.info("Cannot extract local CB data - allowing persistence");
            return null;
        }

        logger.info("Looking for duplicate with:");
        logger.info("  - Period: {} to {}", localData.startDateTime, localData.endDateTime);
        logger.info("  - ProductId: {}", localData.productId);
        logger.info("  - Amount: {}", localData.amount);
        logger.info("  - BuyerId: {}", localData.buyerId);

        // Search for duplicate in TMF
        final CustomerBill[] found = {null};
        final AtomicBoolean stop = new AtomicBoolean(false);

        try {
            tmfDataRetriever.fetchCustomerBills(null, null, 50, candidate -> {
                if (stop.get()) return; // Early exit if already found

                try {
                    boolean isMatch = checkCandidateMatch(candidate, localData);
                    if (isMatch) {
                        found[0] = candidate;
                        stop.set(true);
                        logger.info(">>> DUPLICATE MATCH FOUND: TMF CB id={}", candidate.getId());
                    }
                } catch (Exception e) {
                    logger.warn("Error checking candidate CB {}: {}", candidate.getId(), e.getMessage());
                }
            });

        } catch (Exception e) {
            logger.error("Error fetching CustomerBills from TMF: {}", e.getMessage(), e);
            throw new ExternalServiceException("Failed to search CustomerBill in TMF", e);
        }

        if (found[0] == null) {
            logger.info("=== No duplicate found in TMF ===");
        }
        
        return found[0];
    }

    /**
     * Extracts all necessary comparison data from the local CustomerBill.
     * Returns null if any required field is missing (which means we allow persistence).
     */
    private LocalCbData extractLocalCbData(CustomerBill cb, String revenueBillId) throws Exception {
        // Check billing period
        if (cb.getBillingPeriod() == null) {
            logger.warn("Local CB has null billingPeriod - allowing persistence");
            return null;
        }
        
        OffsetDateTime startDateTime = cb.getBillingPeriod().getStartDateTime();
        OffsetDateTime endDateTime = cb.getBillingPeriod().getEndDateTime();
        
        if (startDateTime == null || endDateTime == null) {
            logger.warn("Local CB has incomplete billingPeriod (start={}, end={}) - allowing persistence", 
                startDateTime, endDateTime);
            return null;
        }

        // Get product ID from ACBRs
        List<AppliedCustomerBillingRate> localAcbrs = billService.getACBRsByRevenueBillId(revenueBillId);
        String productId = null;
        if (!localAcbrs.isEmpty() && localAcbrs.get(0).getProduct() != null) {
            productId = localAcbrs.get(0).getProduct().getId();
        }
        
        if (productId == null) {
            logger.warn("No productId found for RevenueBill {} - allowing persistence", revenueBillId);
            return null;
        }

        // Get amount (Float type)
        Float amount = cb.getTaxExcludedAmount() != null 
            ? cb.getTaxExcludedAmount().getValue() 
            : null;

        // Get buyer ID
        String buyerId = getRelatedPartyIdByRole(cb.getRelatedParty(), Role.BUYER);

        return new LocalCbData(
            startDateTime.truncatedTo(ChronoUnit.SECONDS),
            endDateTime.truncatedTo(ChronoUnit.SECONDS),
            productId,
            amount,
            buyerId
        );
    }

    /**
     * Checks if a candidate CustomerBill from TMF matches the local CB data.
     * All conditions must match for it to be considered a duplicate.
     */
    private boolean checkCandidateMatch(CustomerBill candidate, LocalCbData localData) throws Exception {
        logger.debug("Checking candidate CB: {}", candidate.getId());

        // STEP 1: Check billing period
        if (candidate.getBillingPeriod() == null) {
            logger.debug("  [SKIP] Candidate has null billingPeriod");
            return false;
        }
        
        OffsetDateTime candStart = candidate.getBillingPeriod().getStartDateTime();
        OffsetDateTime candEnd = candidate.getBillingPeriod().getEndDateTime();
        
        if (candStart == null || candEnd == null) {
            logger.debug("  [SKIP] Candidate has incomplete billingPeriod");
            return false;
        }
        
        candStart = candStart.truncatedTo(ChronoUnit.SECONDS);
        candEnd = candEnd.truncatedTo(ChronoUnit.SECONDS);
        
        boolean periodMatch = localData.startDateTime.equals(candStart) && localData.endDateTime.equals(candEnd);
        logger.debug("  Period: local=[{} - {}] vs candidate=[{} - {}] => match={}", 
            localData.startDateTime, localData.endDateTime, candStart, candEnd, periodMatch);
        
        if (!periodMatch) {
            logger.debug("  [SKIP] Period mismatch");
            return false;
        }

        // STEP 2: Check amount (taxExcludedAmount) - using Float
        Float candAmount = candidate.getTaxExcludedAmount() != null 
            ? candidate.getTaxExcludedAmount().getValue() 
            : null;
        
        boolean amountMatch = floatsEqual(localData.amount, candAmount);
        logger.debug("  Amount: local={} vs candidate={} => match={}", localData.amount, candAmount, amountMatch);
        
        if (!amountMatch) {
            logger.debug("  [SKIP] Amount mismatch");
            return false;
        }

        // STEP 3: Check product ID (requires API call, so check after quick filters)
        List<AppliedCustomerBillingRate> candAcbrs = tmfDataRetriever.getACBRsByCustomerBillId(candidate.getId());
        String candProductId = null;
        if (!candAcbrs.isEmpty() && candAcbrs.get(0).getProduct() != null) {
            candProductId = candAcbrs.get(0).getProduct().getId();
        }
        
        boolean productMatch = Objects.equals(localData.productId, candProductId);
        logger.debug("  ProductId: local={} vs candidate={} => match={}", localData.productId, candProductId, productMatch);
        
        if (!productMatch) {
            logger.debug("  [SKIP] ProductId mismatch");
            return false;
        }

        // STEP 4: Check buyer (related party with BUYER role)
        String candBuyerId = getRelatedPartyIdByRole(candidate.getRelatedParty(), Role.BUYER);
        boolean buyerMatch = Objects.equals(localData.buyerId, candBuyerId);
        logger.debug("  BuyerId: local={} vs candidate={} => match={}", localData.buyerId, candBuyerId, buyerMatch);
        
        if (!buyerMatch) {
            logger.debug("  [SKIP] BuyerId mismatch");
            return false;
        }

        // All checks passed - this is a duplicate
        logger.info("  [MATCH] All conditions match - this is a DUPLICATE");
        return true;
    }

    /**
     * Compare two Float amounts for equality.
     * Uses a small tolerance to handle floating point precision issues.
     * If either value is null, returns false (allows persistence when amount is missing).
     * 
     * @param amount1 first amount
     * @param amount2 second amount
     * @return true if amounts are equal within tolerance, false otherwise
     */
    private boolean floatsEqual(Float amount1, Float amount2) {
        if (amount1 == null || amount2 == null) {
            // If either is null, don't consider it a match - allow persistence
            return false;
        }
        // Use tolerance for floating point comparison
        return Math.abs(amount1 - amount2) < AMOUNT_TOLERANCE;
    }

    /**
     * Find a duplicate ACBR in TMF.
     * @param acbr the local AppliedCustomerBillingRate
     * @return the matched ACBR, or null if no match
     */
    private AppliedCustomerBillingRate findAcbrDuplicateInTMF(AppliedCustomerBillingRate acbr) throws Exception {
        logger.debug("findAcbrDuplicateInTMF called");
        
        if (acbr.getPeriodCoverage() == null || acbr.getPeriodCoverage().getStartDateTime() == null) {
            logger.warn("ACBR has no periodCoverage.startDateTime - allowing persistence");
            return null;
        }
        
        Map<String, String> filter = Map.of(
            "periodCoverage.startDateTime", 
            acbr.getPeriodCoverage().getStartDateTime().truncatedTo(ChronoUnit.SECONDS).toString()
        );
        
        AppliedCustomerBillingRate result = FetchUtils.streamAll(
                appliedCustomerBillRateApis::listAppliedCustomerBillingRates, null, filter, 5)
            .filter(candidate -> acbrFieldsMatch(acbr, candidate))
            .findFirst()
            .orElse(null);
        
        logger.debug("findAcbrDuplicateInTMF result: {}", result != null ? result.getId() : "NOT FOUND");
        return result;
    }

    /**
     * Compare two ACBRs on key fields: product, periodCoverage, billingAccount, type, amounts.
     */
    private static boolean acbrFieldsMatch(AppliedCustomerBillingRate acbr1, AppliedCustomerBillingRate acbr2) {
        Map<String, String> map1 = buildAcbrComparisonMap(acbr1);
        Map<String, String> map2 = buildAcbrComparisonMap(acbr2);
        return mapsMatch(map1, map2);
    }

    /**
     * Build a map of fields to compare for an ACBR.
     */
    private static Map<String, String> buildAcbrComparisonMap(AppliedCustomerBillingRate acbr) {
        Map<String, String> map = new HashMap<>();
        
        if (acbr.getProduct() != null && acbr.getProduct().getId() != null) {
            map.put("product.id", acbr.getProduct().getId());
        }
        
        if (acbr.getPeriodCoverage() != null) {
            if (acbr.getPeriodCoverage().getStartDateTime() != null) {
                map.put("periodCoverage.startDateTime",
                    acbr.getPeriodCoverage().getStartDateTime().truncatedTo(ChronoUnit.SECONDS).toString());
            }
            if (acbr.getPeriodCoverage().getEndDateTime() != null) {
                map.put("periodCoverage.endDateTime",
                    acbr.getPeriodCoverage().getEndDateTime().truncatedTo(ChronoUnit.SECONDS).toString());
            }
        }
        
        if (acbr.getBillingAccount() != null && acbr.getBillingAccount().getId() != null) {
            map.put("billingAccount.id", acbr.getBillingAccount().getId());
        }
        
        if (acbr.getType() != null) {
            map.put("type", acbr.getType());
        }
        
        if (acbr.getTaxExcludedAmount() != null && acbr.getTaxExcludedAmount().getValue() != null) {
            map.put("taxExcludedAmount.value", acbr.getTaxExcludedAmount().getValue().toString());
        }
        
        if (acbr.getTaxIncludedAmount() != null && acbr.getTaxIncludedAmount().getValue() != null) {
            map.put("taxIncludedAmount.value", acbr.getTaxIncludedAmount().getValue().toString());
        }
        
        return map;
    }

    /**
     * Compare two maps - all non-null values must match.
     */
    private static boolean mapsMatch(Map<String, String> map1, Map<String, String> map2) {
        for (String key : map1.keySet()) {
            if (map1.get(key) != null && !map1.get(key).equals(map2.get(key))) {
                return false;
            }
        }
        for (String key : map2.keySet()) {
            if (map2.get(key) != null && !map2.get(key).equals(map1.get(key))) {
                return false;
            }
        }
        return true;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Filter bills by date criteria: not in the future and not too old.
     */
    private List<RevenueBill> filterBillsByDate(List<RevenueBill> allBills, OffsetDateTime now, OffsetDateTime cutoffDate) {
        List<RevenueBill> billsToProcess = new ArrayList<>();
        int futureBills = 0;
        int tooOldBills = 0;
        
        for (int idx = 0; idx < allBills.size(); idx++) {
            RevenueBill bill = allBills.get(idx);
            try {
                Object period = bill.getClass().getMethod("getPeriod").invoke(bill);
                if (period != null) {
                    OffsetDateTime startDt = (OffsetDateTime) period.getClass().getMethod("getStartDateTime").invoke(period);
                    OffsetDateTime endDt = (OffsetDateTime) period.getClass().getMethod("getEndDateTime").invoke(period);
                    
                    // Check if bill is in the future
                    if (endDt != null && endDt.isAfter(now)) {
                        logger.debug("[{}] SKIPPED - Future bill: {} - {}", (idx + 1), startDt, endDt);
                        futureBills++;
                        continue;
                    }
                    
                    // Check if bill is too old
                    if (endDt != null && endDt.isBefore(cutoffDate)) {
                        logger.debug("[{}] SKIPPED - Too old: {} - {}", (idx + 1), startDt, endDt);
                        tooOldBills++;
                        continue;
                    }
                    
                    billsToProcess.add(bill);
                    logger.info("[{}] TO PROCESS: {} - {}", (idx + 1), startDt, endDt);
                }
            } catch (Exception e) {
                logger.warn("Could not read bill info: {}", e.getMessage());
            }
        }
        
        logger.info("=== FILTERING SUMMARY ===");
        logger.info("Total bills found: {}", allBills.size());
        logger.info("Bills to process: {}", billsToProcess.size());
        logger.info("Future bills skipped: {}", futureBills);
        logger.info("Too old bills skipped (older than {} months): {}", monthsBack, tooOldBills);
        
        return billsToProcess;
    }

    /**
     * Persist all ACBRs associated with a revenue bill.
     */
    private void persistAssociatedACBRs(String revenueBillId, CustomerBill persistedCB) {
        logger.info("Persisting ACBRs for CB {}...", persistedCB.getId());
        
        List<AppliedCustomerBillingRate> acbrs;
        try {
            acbrs = billService.getACBRsByRevenueBillId(revenueBillId);
        } catch (Exception e) {
            logger.error("Failed to get ACBRs for RevenueBill {}: {}", revenueBillId, e.getMessage());
            return;
        }
        
        logger.info("Found {} ACBRs to persist", acbrs.size());
        
        int success = 0;
        int failed = 0;
        
        for (AppliedCustomerBillingRate acbr : acbrs) {
            try {
                BillRef billRef = new BillRef();
                billRef.setId(persistedCB.getId());
                acbr.setBill(billRef);
                acbr.setIsBilled(true);
                
                persistAppliedCustomerBillingRate(acbr);
                success++;
            } catch (Exception e) {
                failed++;
                logger.error("Error persisting ACBR: {}", e.getMessage(), e);
            }
        }
        
        logger.info("ACBRs persistence complete: {} success, {} failed", success, failed);
    }

    /**
     * Get related party ID by role.
     */
    private String getRelatedPartyIdByRole(List<RelatedParty> relatedParties, Role role) {
        if (relatedParties == null || role == null) return null;
        
        for (RelatedParty rp : relatedParties) {
            if (rp != null && rp.getRole() != null && role.getValue().equalsIgnoreCase(rp.getRole())) {
                return rp.getId();
            }
        }
        return null;
    }

    /**
     * Add watermark to CustomerBill for tracking.
     */
    private static CustomerBill watermark(CustomerBill cb) {
        if (cb.getCategory() != null) {
            cb.setCategory(cb.getCategory() + " - " + WATERMARK);
        } else {
            cb.setCategory(WATERMARK);
        }
        return cb;
    }

    /**
     * Add watermark to ACBR for tracking.
     */
    private static AppliedCustomerBillingRate watermark(AppliedCustomerBillingRate acbr) {
        if (acbr.getDescription() != null) {
            acbr.setDescription(acbr.getDescription() + " - " + WATERMARK);
        } else {
            acbr.setDescription(WATERMARK);
        }
        return acbr;
    }

    /**
     * Compute the next available bill number from TMF.
     */
    private String computeNextBillNoFromTMF() throws Exception {
        int year = OffsetDateTime.now().getYear();
        String prefix = "INV-" + year + "-";
        final int[] max = {0};

        Map<String, String> filter = new HashMap<>();
        filter.put("category", WATERMARK);

        tmfDataRetriever.fetchCustomerBills(null, filter, 50, cb -> {
            String billNo = cb.getBillNo();
            if (billNo != null && billNo.startsWith(prefix)) {
                try {
                    int n = Integer.parseInt(billNo.substring(prefix.length()));
                    if (n > max[0]) max[0] = n;
                } catch (NumberFormatException e) {
                    logger.warn("Invalid billNo format: {}", billNo);
                }
            }
        });

        return String.format("INV-%d-%04d", year, max[0] + 1);
    }

    /**
     * Increment the bill number sequence.
     */
    private String incrementBillNo(String billNo) {
        String[] parts = billNo.split("-");
        int seq = Integer.parseInt(parts[2]) + 1;
        return String.format("%s-%s-%04d", parts[0], parts[1], seq);
    }

    /**
     * Log CustomerBill details for debugging.
     */
    private void logCustomerBillDetails(String prefix, CustomerBill cb) {
        logger.info("{}:", prefix);
        logger.info("  - id: {}", cb.getId());
        logger.info("  - billDate: {}", cb.getBillDate());
        logger.info("  - billingPeriod: {}", 
            cb.getBillingPeriod() != null 
                ? cb.getBillingPeriod().getStartDateTime() + " - " + cb.getBillingPeriod().getEndDateTime() 
                : "NULL");
        logger.info("  - taxExcludedAmount: {}", 
            cb.getTaxExcludedAmount() != null ? cb.getTaxExcludedAmount().getValue() : "NULL");
        logger.info("  - relatedParties: {}", 
            cb.getRelatedParty() != null ? cb.getRelatedParty().size() : 0);
    }

    /**
     * Log final summary of persistence operation.
     */
    private void logFinalSummary(int totalBills, int filteredBills, int persistedBills) {
        logger.info("\n=== FINAL SUMMARY ===");
        logger.info("Total bills found: {}", totalBills);
        logger.info("Filtered for processing: {}", filteredBills);
        logger.info("Successfully persisted: {}", persistedBills);
        logger.info("Skipped (duplicates or errors): {}", filteredBills - persistedBills);
    }

    // ==================== INNER CLASSES ====================

    /**
     * Data holder for local CustomerBill comparison values.
     * Uses Float for amount to match the TMF model type.
     */
    private static class LocalCbData {
        final OffsetDateTime startDateTime;
        final OffsetDateTime endDateTime;
        final String productId;
        final Float amount;
        final String buyerId;

        LocalCbData(OffsetDateTime startDateTime, OffsetDateTime endDateTime, 
                   String productId, Float amount, String buyerId) {
            this.startDateTime = startDateTime;
            this.endDateTime = endDateTime;
            this.productId = productId;
            this.amount = amount;
            this.buyerId = buyerId;
        }
    }
}