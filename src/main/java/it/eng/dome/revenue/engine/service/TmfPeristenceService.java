package it.eng.dome.revenue.engine.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.APIPartyApis;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
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
 * FIXME: Enhancements and fixes:
 * [H] only consider active subscriptions ([L] but be careful with last invoices... sub might not be active)
 */
@Service
public class TmfPeristenceService {

    private final Logger logger = LoggerFactory.getLogger(TmfPeristenceService.class);

    @Autowired 
    private BillsService billService;
    
    @Autowired 
    private CachedSubscriptionService subscriptionService;
    
    @Autowired 
    private TmfCachedDataRetriever tmfDataRetriever;

    @Value("${persistence.monthsBack:2}")
    private int monthsBack;

    private APIPartyApis apiPartyApis;
    private CustomerBillApis customerBillApis;
    private AppliedCustomerBillRateApis appliedCustomerBillRateApis;

    public TmfPeristenceService(APIPartyApis apiPartyApis, CustomerBillApis customerBillApis,
                                AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
        this.apiPartyApis = apiPartyApis;
        this.customerBillApis = customerBillApis;
        this.appliedCustomerBillRateApis = appliedCustomerBillRateApis;
    }

    /**
     * Persists all revenue bills for all organizations.
     * Uses batch processing to fetch organizations and persist bills on the fly.
     */
    public List<CustomerBill> persistAllRevenueBills() throws Exception {
        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        FetchUtils.fetchByBatch(apiPartyApis::listOrganizations, null, null, 100,
            batch -> batch.forEach(org -> {
                try {
                    createdCustomerBills.addAll(persistProviderRevenueBills(org.getId()));
                } catch (Exception e) {
                    logger.error("Error: {}", e.getMessage());
                }
            })
        );
        return createdCustomerBills;
    }

    /**
     * Persist all revenue bills for a provider; where needed and applicable.
     * @param providerId the provider id
     */
    public List<CustomerBill> persistProviderRevenueBills(String providerId) throws Exception {
        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        // FIXME: consider filtering only active subscriptions
        List<Subscription> buyerSubscriptions = RelatedPartyUtils.retainSubscriptionsWithParty(
                this.subscriptionService.getAllSubscriptions(), providerId, Role.BUYER);
        for (Subscription sub : buyerSubscriptions) {
            createdCustomerBills.addAll(this.persistSubscriptionRevenueBills(sub.getId()));
        }
        return createdCustomerBills;
    }

    /**
     * Persist all revenue bills for a subscription; where needed and applicable.
     * @param subscriptionId
     */
    public List<CustomerBill> persistSubscriptionRevenueBills(String subscriptionId) throws Exception {
        OffsetDateTime startDate = OffsetDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC)
                .minusMonths(monthsBack);

        return billService.getSubscriptionBills(subscriptionId).parallelStream()
                .filter(sb -> sb.getPeriod() == null
                        || sb.getPeriod().getEndDateTime().isAfter(startDate)
                        || sb.getPeriod().getStartDateTime().isAfter(startDate))
                .map(sb -> {
                    try {
                        return persistRevenueBill(sb.getId());
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Persist a revenue bill; where needed and applicable.
     * @param revenueBillId the revenue bill id
     */
    public CustomerBill persistRevenueBill(String revenueBillId) throws Exception {
        CustomerBill localCb = billService.getCustomerBillByRevenueBillId(revenueBillId);

        if (localCb.getBillDate().isAfter(OffsetDateTime.now())) {
            logger.info("Skipping CB {} because not yet consolidated.", revenueBillId);
            return null;
        }

        CustomerBill persistedCB = persistCustomerBill(localCb, revenueBillId);

        if (persistedCB != null) {
            // generate the ACBRs
            List<AppliedCustomerBillingRate> acbrs = billService.getACBRsByRevenueBillId(revenueBillId);
            for (AppliedCustomerBillingRate acbr : acbrs) {
                BillRef billRef = new BillRef();
                billRef.setId(persistedCB.getId());
                acbr.setBill(billRef);
                acbr.setIsBilled(true);
                persistAppliedCustomerBillingRate(acbr);
            }

        } else {
            logger.debug("***No ACBR was created because CB already exists");
        }
        return persistedCB;
    }

    /**
     * Persist a CustomerBill if not already present on TMF. 
     */
    public CustomerBill persistCustomerBill(CustomerBill cb, String revenueBillId) throws Exception {
        CustomerBill existingCustomerBill = isCbAlreadyInTMF(cb, revenueBillId);
        if (existingCustomerBill == null) {
            CustomerBill cbToPersist = watermark(cb);
            String id = customerBillApis.createCustomerBill(CustomerBillCreate.fromJson(cbToPersist.toJson()));
            logger.info("PERSISTENCE: created CB with id {}", id);
            return tmfDataRetriever.getCustomerBillById(id);
        } else {
            logger.info("Local CB {} is already on TMF with id {}", cb.getId(), existingCustomerBill.getId());
            return null;
        }
    }

    /**
     * Persist an AppliedCustomerBillingRate if not already present on TMF. 
     */
    public void persistAppliedCustomerBillingRate(AppliedCustomerBillingRate acbr) throws Exception {
        AppliedCustomerBillingRate existingACBR = isAcbrAlreadyInTMF(acbr);

        if (existingACBR == null) {
            AppliedCustomerBillingRate acbrToPersist = watermark(acbr);
            AppliedCustomerBillingRateCreate acbrc = AppliedCustomerBillingRateCreate.fromJson(acbrToPersist.toJson());
            acbrc.setAtSchemaLocation(new URI(
                    "https://raw.githubusercontent.com/DOME-Marketplace/dome-odrl-profile/refs/heads/add-related-party-ref/schemas/simplified/RelatedPartyRef.schema.json"));
            String createdId = appliedCustomerBillRateApis.createAppliedCustomerBillingRate(acbrc);
            logger.info("PERSISTENCE: created ACBR with id {}", createdId);
        } else {
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
     * Checks if a given CustomerBill already exists in TMF.
     * Stops fetching batches as soon as a match is found (early stop).
     */
    private CustomerBill isCbAlreadyInTMF(CustomerBill cb, String revenueBillId) throws Exception {
        List<AppliedCustomerBillingRate> localAcbrs = billService.getACBRsByRevenueBillId(revenueBillId);
        String localProductId = localAcbrs.isEmpty() || localAcbrs.get(0).getProduct() == null
                ? null
                : localAcbrs.get(0).getProduct().getId();

        return FetchUtils.streamAll(customerBillApis::listCustomerBills, null, null, 100)
                .filter(candidate -> {
                    try {
                        // Truncate CB dates to seconds
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

                        boolean periodMatch = Objects.equals(cbStart, candStart) && Objects.equals(cbEnd, candEnd);

                        // Compare product IDs of first ACBR
                        List<AppliedCustomerBillingRate> candAcbrs = tmfDataRetriever.getACBRsByCustomerBillId(candidate.getId());
                        String candProductId = candAcbrs.isEmpty() || candAcbrs.get(0).getProduct() == null
                                ? null
                                : candAcbrs.get(0).getProduct().getId();

                        return periodMatch
                                && Objects.equals(localProductId, candProductId)
                                && relatedPartyMatch(cb.getRelatedParty(), candidate.getRelatedParty());
                    } catch (Exception e) {
                        logger.error("Error in isCbAlreadyInTMF filter: {}", e.getMessage());
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }
    
    /**
	 * Retrieve ACBRs on TMF that, potentially match the local ACBR. 
	 * @param acbr the local AppliedCustomerBillingRate
	 * @return the matched ACBR, or null if no match.
     * If more than one match is found, an exception is raised.
     *
     * @throws Exception exception in case of error
	 */
    private AppliedCustomerBillingRate isAcbrAlreadyInTMF(AppliedCustomerBillingRate acbr) throws Exception {
        Map<String, String> filter = Map.of(
                "periodCoverage.startDateTime", acbr.getPeriodCoverage().getStartDateTime().truncatedTo(ChronoUnit.SECONDS).toString()
        );
        return FetchUtils.streamAll(appliedCustomerBillRateApis::listAppliedCustomerBillingRates, null, filter, 10)
                .filter(candidate -> match(acbr, candidate))
                .findFirst()
                .orElse(null);
    }

    
    /**
     *  Compare on the following fields: product, periodcoverage, name, description, type, taxExcludedAmount
     */
    private static boolean match(AppliedCustomerBillingRate acbr1, AppliedCustomerBillingRate acbr2) {
        Map<String, String> acbr1map = buildComparisonMap(acbr1);
        Map<String, String> acbr2map = buildComparisonMap(acbr2);
        return mapsMatch(acbr1map, acbr2map);
    }

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

    private static boolean mapsMatch(Map<String, String> map1, Map<String, String> map2) {
        // check all elements in map1 are also in map2 with the same value
        for(String k:map1.keySet()) {
            if(map1.get(k)!=null && !map1.get(k).equals(map2.get(k)))
                return false;
        }
        // check all elements in map2 are also in map1 with the same value
        for(String k:map2.keySet()) {
            if(map2.get(k)!=null && !map2.get(k).equals(map1.get(k)))
                return false;
        }
        return true;
    }

    private static AppliedCustomerBillingRate watermark(AppliedCustomerBillingRate acbr) {
        // FIXME: marking ACBR for dev, remove before flight

        String mark = "Created by the Revenue Engine";
        if(acbr.getDescription()!=null) {
            acbr.setDescription(acbr.getDescription() + " - " + mark);
        } else {
            acbr.setDescription(mark);
        }
        return acbr;
    }

    private static CustomerBill watermark(CustomerBill cb) {
        // FIXME: marking ACBR for dev, remove before flight

        String mark = "Created by the Revenue Engine";
        if(cb.getCategory()!=null) {
            cb.setCategory(cb.getCategory() + " - " + mark);
        } else {
            cb.setCategory(mark);
        }
        return cb;
    }

    private boolean relatedPartyMatch(List<RelatedParty> rl1, List<RelatedParty> rl2) {
        String rlId1 = this.getRelatedPartyIdByRole(rl1, Role.BUYER);
        String rlId2 = this.getRelatedPartyIdByRole(rl2, Role.BUYER);

        return rlId1.equals(rlId2);
    }

    private String getRelatedPartyIdByRole(List<RelatedParty> relatedParties, Role role) {
        if (relatedParties == null || role == null) return null;
        for (RelatedParty rp : relatedParties) {
            if (rp != null && rp.getRole() != null && role.getValue().equalsIgnoreCase(rp.getRole()))
                return rp.getId();
        }
        return null;
    }
}
