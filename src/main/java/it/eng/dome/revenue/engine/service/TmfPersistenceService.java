package it.eng.dome.revenue.engine.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.APIPartyApis;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
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
 */
@Service
public class TmfPersistenceService {

    private final Logger logger = LoggerFactory.getLogger(TmfPersistenceService.class);
    
    private static final String SCHEMA_LOCATION = "https://raw.githubusercontent.com/DOME-Marketplace/dome-odrl-profile/refs/heads/add-related-party-ref/schemas/simplified/RelatedPartyRef.schema.json";

    @Autowired 
    private BillsService billService;
    
    @Autowired 
    private CachedSubscriptionService subscriptionService;
    
    @Autowired 
    private TmfCachedDataRetriever tmfDataRetriever;

    @Value("${persistence.monthsBack:12}")
    private int monthsBack;

    private APIPartyApis apiPartyApis;
    private CustomerBillApis customerBillApis;
    private AppliedCustomerBillRateApis appliedCustomerBillRateApis;

    public TmfPersistenceService(APIPartyApis apiPartyApis, CustomerBillApis customerBillApis,
                                AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
        this.apiPartyApis = apiPartyApis;
        this.customerBillApis = customerBillApis;
        this.appliedCustomerBillRateApis = appliedCustomerBillRateApis;
    }

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
                    logger.info("  → Created {} CBs for org {}", orgBills.size(), org.getId());
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
                logger.info("  Processing subscription: {}", sub.getId());
                List<CustomerBill> subBills = this.persistSubscriptionRevenueBills(sub.getId());
                createdCustomerBills.addAll(subBills);
                logger.info("    → Created {} CBs for subscription {}", subBills.size(), sub.getId());
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
     * @param subscriptionId the subscription id
     */
    public List<CustomerBill> persistSubscriptionRevenueBills(String subscriptionId) throws Exception {
        logger.info("=== START persistSubscriptionRevenueBills for subscription {} ===", subscriptionId);

        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        
        try {
            // Get all bills for subscription
            List<RevenueBill> allBills = billService.getSubscriptionBills(subscriptionId);
            logger.info("Found {} total bills for subscription", allBills.size());
            
            // Debug: log each bill's ID and period
            for (int idx = 0; idx < allBills.size(); idx++) {
                Object bill = allBills.get(idx);
                try {
                    String billId = (String) bill.getClass().getMethod("getId").invoke(bill);
                    Object period = bill.getClass().getMethod("getPeriod").invoke(bill);
                    logger.info("  [{}] Bill ID: {}", (idx + 1), billId);
                    if (period != null) {
                        OffsetDateTime startDt = (OffsetDateTime) period.getClass().getMethod("getStartDateTime").invoke(period);
                        OffsetDateTime endDt = (OffsetDateTime) period.getClass().getMethod("getEndDateTime").invoke(period);
                        logger.info("       Period: {} - {}", startDt, endDt);
                    }
                } catch (Exception e) {
                    logger.warn("Could not read bill info: {}", e.getMessage());
                }
            }

            // Process each bill
            for (int i = 0; i < allBills.size(); i++) {
                Object sb = allBills.get(i);
                String billId = null;
                
                try {
                    billId = (String) sb.getClass().getMethod("getId").invoke(sb);
                    logger.info("\n>>> Processing bill {}/{}: {}", (i + 1), allBills.size(), billId);
                    
                    try {
                        CustomerBill persisted = persistRevenueBill(billId);
                        if (persisted != null) {
                            createdCustomerBills.add(persisted);
                            logger.info("✅ [{}] Successfully persisted CB: {}", (i + 1), persisted.getId());
                        } else {
                            logger.warn("⚠️  [{}] CB not persisted (null returned)", (i + 1));
                        }
                    } catch (Exception e) {
                        logger.error("❌ [{}] EXCEPTION in persistRevenueBill: {}", (i + 1), e.getMessage());
                        logger.error("   Full stack trace: ", e);
                        throw e;
                    }
                } catch (Exception e) {
                    logger.error("❌ [{}] FATAL Error processing bill {}: {}", (i + 1), billId, e.getMessage());
                    // Continue processing other bills even if one fails
                }
            }
            
            logger.info("\n=== SUMMARY ===");
            logger.info("Total bills: {}", allBills.size());
            logger.info("Successfully persisted: {}", createdCustomerBills.size());
            logger.info("Failed/Skipped: {}", allBills.size() - createdCustomerBills.size());
        } catch (Exception e) {
            logger.error("Error in persistSubscriptionRevenueBills: {}", e.getMessage(), e);
            throw e;
        }
        
        logger.info("=== END persistSubscriptionRevenueBills: Created {} CBs ===", createdCustomerBills.size());
        return createdCustomerBills;
    }

    /**
     * Persist a revenue bill; where needed and applicable.
     * @param revenueBillId the revenue bill id
     */
    public CustomerBill persistRevenueBill(String revenueBillId) throws Exception {
        logger.debug("persistRevenueBill called for: {}", revenueBillId);
        
        try {
            // Get the local CustomerBill from RevenueBill
            CustomerBill localCb = billService.getCustomerBillByRevenueBillId(revenueBillId);
            if (localCb == null) {
                logger.warn("  ⚠️  getCustomerBillByRevenueBillId returned null!");
                return null;
            }
            logger.debug("  Created local CB from RevenueBill, billDate: {}", localCb.getBillDate());

            // Persist the CustomerBill
            CustomerBill persistedCB = persistCustomerBill(localCb, revenueBillId);

            // If CB was persisted, persist associated ACBRs
            if (persistedCB != null) {
                logger.debug("  CB persisted successfully, now persisting ACBRs");
                
                // Get ACBRs for this RevenueBill
                List<AppliedCustomerBillingRate> acbrs = billService.getACBRsByRevenueBillId(revenueBillId);
                logger.debug("  Found {} ACBRs to persist", acbrs.size());
                
                for (AppliedCustomerBillingRate acbr : acbrs) {
                    try {
                        // Set reference to persisted CB
                        BillRef billRef = new BillRef();
                        billRef.setId(persistedCB.getId());
                        acbr.setBill(billRef);
                        acbr.setIsBilled(true);
                        
                        persistAppliedCustomerBillingRate(acbr);
                        logger.debug("    ✅ ACBR persisted");
                    } catch (Exception e) {
                        logger.error("Error persisting ACBR: {}", e.getMessage(), e);
                    }
                }
            } else {
                logger.warn("  ⚠️  CB not persisted (already exists in TMF or error), skipping ACBRs");
            }
            
            return persistedCB;
        } catch (Exception e) {
            logger.error("  ❌ ERROR in persistRevenueBill: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Persist a CustomerBill if not already present on TMF. 
     * @param cb the local CustomerBill
     * @param revenueBillId the associated revenue bill id for product comparison
     * @return the persisted CustomerBill, or null if already present
     */
    public CustomerBill persistCustomerBill(CustomerBill cb, String revenueBillId) throws Exception {
        logger.debug("persistCustomerBill called for RevenueBill {}", revenueBillId);
        
        if (cb == null) {
            logger.warn("CustomerBill is null for RevenueBill {}, skipping", revenueBillId);
            return null;
        }

        // Check if already exists in TMF
        CustomerBill existingCustomerBill = isCbAlreadyInTMF(cb, revenueBillId);
        
        if (existingCustomerBill == null) {
            // Not in TMF → persist it
            logger.debug("CB not found in TMF, proceeding with persistence");
            CustomerBill cbToPersist = watermark(cb);
            String id = customerBillApis.createCustomerBill(CustomerBillCreate.fromJson(cbToPersist.toJson()));
            logger.info("PERSISTENCE: created CB with id {}", id);
            return tmfDataRetriever.getCustomerBill(id);
        } else {
            // Already in TMF → don't persist
            logger.info("Local CB {} is already on TMF with id {}", cb.getId(), existingCustomerBill.getId());
            return null;
        }
    }

    /**
     * Persist an AppliedCustomerBillingRate if not already present on TMF. 
     * @param acbr the local AppliedCustomerBillingRate
     * @throws Exception in case of error
     */
    public void persistAppliedCustomerBillingRate(AppliedCustomerBillingRate acbr) throws Exception {
        logger.debug("persistAppliedCustomerBillingRate called");
        
        AppliedCustomerBillingRate existingACBR = isAcbrAlreadyInTMF(acbr);

        if (existingACBR == null) {
            // Not in TMF → persist it
            logger.debug("ACBR not found in TMF, proceeding with persistence");
            AppliedCustomerBillingRate acbrToPersist = watermark(acbr);
            AppliedCustomerBillingRateCreate acbrc = AppliedCustomerBillingRateCreate.fromJson(acbrToPersist.toJson());
            acbrc.setAtSchemaLocation(new URI(SCHEMA_LOCATION));
            String createdId = appliedCustomerBillRateApis.createAppliedCustomerBillingRate(acbrc);
            logger.info("PERSISTENCE: created ACBR with id {}", createdId);
        } else {
            // Already in TMF → don't persist
            logger.info("Local ACBR {} is already on TMF with id {}", acbr.getId(), existingACBR.getId());
        }
    }

    /**
     * Checks if a given CustomerBill already exists in TMF.
     * Uses early-stop fetching: stops as soon as a match is found.
     *
     * @param cb the local CustomerBill to check
     * @param revenueBillId the associated revenue bill ID for product comparison
     * @return the matched CustomerBill from TMF, or null if none found
     * @throws Exception if any API call fails
     */
    private CustomerBill isCbAlreadyInTMF(CustomerBill cb, String revenueBillId) throws Exception {
        logger.debug("isCbAlreadyInTMF called for RevenueBill {}", revenueBillId);

        // Retrieve local productId from RevenueBill
        List<AppliedCustomerBillingRate> localAcbrs = billService.getACBRsByRevenueBillId(revenueBillId);
        String localProductId = localAcbrs.isEmpty() || localAcbrs.get(0).getProduct() == null
                ? null
                : localAcbrs.get(0).getProduct().getId();
        logger.debug("  localProductId: {}", localProductId);

        if (localProductId == null) {
            logger.warn("No productId found for RevenueBill {}", revenueBillId);
            return null;
        }

        // Prepare containers for result & loop control
        final CustomerBill[] found = {null};
        final AtomicBoolean stop = new AtomicBoolean(false);

        try {
            // Iterate all CustomerBills in TMF by batch
            tmfDataRetriever.fetchCustomerBills(null, null, 50, candidate -> {
                if (stop.get()) return; // short-circuit if already found

                try {
                    logger.debug("    Checking candidate CB: {}", candidate.getId());
                    
                    // Compare billing periods (truncate to seconds for precision consistency)
                    OffsetDateTime cbStart = cb.getBillingPeriod() != null
                            ? cb.getBillingPeriod().getStartDateTime().truncatedTo(ChronoUnit.SECONDS)
                            : null;
                    OffsetDateTime cbEnd = cb.getBillingPeriod() != null
                            ? cb.getBillingPeriod().getEndDateTime().truncatedTo(ChronoUnit.SECONDS)
                            : null;
                    OffsetDateTime candStart = candidate.getBillingPeriod() != null
                            ? candidate.getBillingPeriod().getStartDateTime().truncatedTo(ChronoUnit.SECONDS)
                            : null;
                    OffsetDateTime candEnd = candidate.getBillingPeriod() != null
                            ? candidate.getBillingPeriod().getEndDateTime().truncatedTo(ChronoUnit.SECONDS)
                            : null;

                    boolean periodMatch = Objects.equals(cbStart, candStart)
                            && Objects.equals(cbEnd, candEnd);
                    logger.debug("      periodMatch: {} ({}-{} vs {}-{})", 
                        periodMatch, cbStart, cbEnd, candStart, candEnd);

                    // Compare Product IDs via first AppliedCustomerBillingRate
                    List<AppliedCustomerBillingRate> candAcbrs =
                            tmfDataRetriever.getACBRsByCustomerBillId(candidate.getId());
                    String candProductId = candAcbrs.isEmpty() || candAcbrs.get(0).getProduct() == null
                            ? null
                            : candAcbrs.get(0).getProduct().getId();
                    logger.debug("      candProductId: {} vs localProductId: {}", candProductId, localProductId);

                    // Compare related parties
                    boolean relatedPartyMatch = relatedPartyMatch(cb.getRelatedParty(), candidate.getRelatedParty());
                    logger.debug("      relatedPartyMatch: {}", relatedPartyMatch);

                    // If all conditions match, store & stop
                    if (periodMatch && Objects.equals(localProductId, candProductId) && relatedPartyMatch) {
                        found[0] = candidate;
                        stop.set(true);
                        logger.debug("      ✅ MATCH FOUND: {}", candidate.getId());
                    }

                } catch (Exception e) {
                    logger.warn("Error while checking CustomerBill {}: {}", candidate.getId(), e.getMessage());
                }
            });

        } catch (Exception e) {
            logger.error("Error during fetchAllCustomerBills: {}", e.getMessage(), e);
            throw new ExternalServiceException("Failed to search CustomerBill in TMF", e);
        }

        logger.debug("  isCbAlreadyInTMF result: {}", found[0] != null ? found[0].getId() : "NOT FOUND");
        return found[0];
    }

    /**
     * Retrieve ACBRs on TMF that potentially match the local ACBR. 
     * @param acbr the local AppliedCustomerBillingRate
     * @return the matched ACBR, or null if no match
     * @throws Exception in case of error
     */
    private AppliedCustomerBillingRate isAcbrAlreadyInTMF(AppliedCustomerBillingRate acbr) throws Exception {
        logger.debug("isAcbrAlreadyInTMF called");
        
        if (acbr.getPeriodCoverage() == null || acbr.getPeriodCoverage().getStartDateTime() == null) {
            logger.warn("ACBR has no periodCoverage.startDateTime, cannot check TMF");
            return null;
        }
        
        Map<String, String> filter = Map.of(
                "periodCoverage.startDateTime", 
                acbr.getPeriodCoverage().getStartDateTime().truncatedTo(ChronoUnit.SECONDS).toString()
        );
        
        AppliedCustomerBillingRate result = FetchUtils.streamAll(appliedCustomerBillRateApis::listAppliedCustomerBillingRates, null, filter, 5)
                .filter(candidate -> match(acbr, candidate))
                .findFirst()
                .orElse(null);
        
        logger.debug("  isAcbrAlreadyInTMF result: {}", result != null ? result.getId() : "NOT FOUND");
        return result;
    }

    /**
     * Compare two ACBRs on the following fields: product, periodcoverage, billingAccount, type, taxExcludedAmount
     * @param acbr1 first ACBR
     * @param acbr2 second ACBR
     * @return true if match, false otherwise
     */
    private static boolean match(AppliedCustomerBillingRate acbr1, AppliedCustomerBillingRate acbr2) {
        Map<String, String> acbr1map = buildComparisonMap(acbr1);
        Map<String, String> acbr2map = buildComparisonMap(acbr2);
        return mapsMatch(acbr1map, acbr2map);
    }

    /**
     * Build a map of fields to compare for an ACBR.
     * @param acbr the ACBR
     * @return map of fields to compare
     */
    private static Map<String, String> buildComparisonMap(AppliedCustomerBillingRate acbr) {
        Map<String, String> map = new HashMap<>();
        
        if (acbr.getProduct() != null && acbr.getProduct().getId() != null)
            map.put("product.id", acbr.getProduct().getId());
        
        if (acbr.getPeriodCoverage() != null) {
            if (acbr.getPeriodCoverage().getStartDateTime() != null)
                map.put("periodCoverage.startDateTime",
                        acbr.getPeriodCoverage().getStartDateTime().truncatedTo(ChronoUnit.SECONDS).toString());
            if (acbr.getPeriodCoverage().getEndDateTime() != null)
                map.put("periodCoverage.endDateTime",
                        acbr.getPeriodCoverage().getEndDateTime().truncatedTo(ChronoUnit.SECONDS).toString());
        }
        
        if (acbr.getBillingAccount() != null && acbr.getBillingAccount().getId() != null)
            map.put("billingAccount.id", acbr.getBillingAccount().getId());
        
        if (acbr.getType() != null)
            map.put("type", acbr.getType());
        
        if (acbr.getTaxExcludedAmount() != null && acbr.getTaxExcludedAmount().getValue() != null)
            map.put("taxExcludedAmount.value", acbr.getTaxExcludedAmount().getValue().toString());
        
        return map;
    }

    /**
     * Compare two maps for matching keys and values.
     * @param map1 first map
     * @param map2 second map
     * @return true if maps match, false otherwise
     */
    private static boolean mapsMatch(Map<String, String> map1, Map<String, String> map2) {
        // check all elements in map1 are also in map2 with the same value
        for (String k : map1.keySet()) {
            if (map1.get(k) != null && !map1.get(k).equals(map2.get(k)))
                return false;
        }
        // check all elements in map2 are also in map1 with the same value
        for (String k : map2.keySet()) {
            if (map2.get(k) != null && !map2.get(k).equals(map1.get(k)))
                return false;
        }
        return true;
    }

    /**
     * Add watermark to ACBR for development tracking
     */
    private static AppliedCustomerBillingRate watermark(AppliedCustomerBillingRate acbr) {
        String mark = "Created by the Revenue Engine";
        if (acbr.getDescription() != null) {
            acbr.setDescription(acbr.getDescription() + " - " + mark);
        } else {
            acbr.setDescription(mark);
        }
        return acbr;
    }

    /**
     * Add watermark to CustomerBill for development tracking
     */
    private static CustomerBill watermark(CustomerBill cb) {
        String mark = "Created by the Revenue Engine";
        if (cb.getCategory() != null) {
            cb.setCategory(cb.getCategory() + " - " + mark);
        } else {
            cb.setCategory(mark);
        }
        return cb;
    }

    /**
     * Compare related parties of two CustomerBills for matching BUYER roles.
     * @param rl1 first list of RelatedParty
     * @param rl2 second list of RelatedParty
     * @return true if BUYER party matches, false otherwise
     */
    private boolean relatedPartyMatch(List<RelatedParty> rl1, List<RelatedParty> rl2) {
        String rlId1 = this.getRelatedPartyIdByRole(rl1, Role.BUYER);
        String rlId2 = this.getRelatedPartyIdByRole(rl2, Role.BUYER);
        return Objects.equals(rlId1, rlId2);
    }

    /**
     * Get related party ID by role.
     * @param relatedParties list of RelatedParty
     * @param role the role to search for
     * @return the related party id for the given role, or null if not found
     */
    private String getRelatedPartyIdByRole(List<RelatedParty> relatedParties, Role role) {
        if (relatedParties == null || role == null) return null;
        for (RelatedParty rp : relatedParties) {
            if (rp != null && rp.getRole() != null && role.getValue().equalsIgnoreCase(rp.getRole()))
                return rp.getId();
        }
        return null;
    }
}