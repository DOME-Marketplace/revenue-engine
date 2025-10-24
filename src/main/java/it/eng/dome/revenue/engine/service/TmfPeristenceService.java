package it.eng.dome.revenue.engine.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;

/**
 * FIXME: Enhancemets and fixes:
 * [H] only consider active subscriptions ([L] but be careful with last invoices... sub might not be active)
 */

@Service
public class TmfPeristenceService {

    private final Logger logger = LoggerFactory.getLogger(TmfPeristenceService.class);

//    @Autowired
//    private TmfApiFactory tmfApiFactory;

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

        //FIXME - to be verified
        FetchUtils.fetchByBatch(
        	apiPartyApis::listOrganizations, 
    	    null, 
    	    null, 
    	    10, 
    	    batch -> {
			    batch.forEach(org -> {
			    	try {
						createdCustomerBills.addAll(this.persistProviderRevenueBills(org.getId()));
					} catch (Exception e) {
						logger.error("Error: {}", e.getMessage());
					}
			    });
			}
    	);

//        TMFApiUtils.fetchByBatch(orgApi::listOrganization, null, 10, null, batch -> {
//            for (Organization org : batch) {
//                createdCustomerBills.addAll(this.persistProviderRevenueBills(org.getId()));
//            }
//            return true; // continue fetching next batch
//        });

        return createdCustomerBills;
    }


    /**
     * Persist all revenue bills for a provider; where needed and applicable.
     * @param providerId the provider id
     */
    public List<CustomerBill> persistProviderRevenueBills(String providerId) throws Exception {
        List<CustomerBill> createdCustomerBills = new ArrayList<>();

        List<Subscription> buyerSubscriptions = RelatedPartyUtils.retainSubscriptionsWithParty(
            this.subscriptionService.getAllSubscriptions(), providerId, Role.BUYER
        );

        for (Subscription sub : buyerSubscriptions) {
            createdCustomerBills.addAll(this.persistSubscriptionRevenueBills(sub.getId()));
        }

        return createdCustomerBills;
    }



    /*
     * Persist all revenue bills for a subscription; where needed and applicable.
     * @param subscriptionId
     */
    public List<CustomerBill> persistSubscriptionRevenueBills(String subscriptionId) throws Exception {
        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime firstDayOfCurrentMonth = now.withDayOfMonth(1)
                                                   .toLocalDate()
                                                   .atStartOfDay()
                                                   .atOffset(now.getOffset());
        OffsetDateTime startDate = firstDayOfCurrentMonth.minusMonths(monthsBack);
        
        for (RevenueBill sb : this.billService.getSubscriptionBills(subscriptionId)) {
            if (sb.getPeriod()!= null && sb.getPeriod().getEndDateTime().isBefore(startDate)) {
                logger.info("Skipping CB {} because older than {}", sb.getId(), startDate);
                continue;
            }
            CustomerBill createdCustomerBill = this.persistRevenueBill(sb.getId());
            if (createdCustomerBill != null) {
                createdCustomerBills.add(createdCustomerBill);
            }
        }

        return createdCustomerBills;
    }


    /**
	 * Persist a revenue bill; where needed and applicable.
	 * @param revenueBillId the revenue bill id
	 */
    public CustomerBill persistRevenueBill(String revenueBillId) throws Exception {


        // retrieve the local cb
        CustomerBill localCb = this.billService.getCustomerBillByRevenueBillId(revenueBillId);

        if(localCb.getBillDate().isAfter(OffsetDateTime.now())) {
            logger.info("Skipping CB {} because not yet consolidated.", revenueBillId);
            return null;
        }

        // persiste the cb and get the id
        CustomerBill persistedCB = this.persistCustomerBill(localCb, revenueBillId);

        if(persistedCB != null) {
	        // generate the acbrs
	        List<AppliedCustomerBillingRate> acbrs = this.billService.getACBRsByRevenueBillId(revenueBillId);
	        for(AppliedCustomerBillingRate acbr: acbrs) {
	            // set the reference to the new persisted cb
	            BillRef bref = new BillRef();
                if (persistedCB.getId() != null)
	                bref.setId(persistedCB.getId());
	            acbr.setBill(bref);
	            // mark it as billed
	            acbr.setIsBilled(true);
	            // persiste the acbr
	            this.persistAppliedCustomerBillingRate(acbr);
	        }
        }
        else
        	logger.debug("***No ACBR was create beacuse cb already exists");
        	
        return persistedCB;
    }

    /*
     * Persist a CustomerBill if not already present on TMF. 
     */
    public CustomerBill persistCustomerBill(CustomerBill cb, String revenueBillId) throws Exception {
        // check if exist on tmf
        logger.debug("PERSISTENCE - look for existing CB");
        CustomerBill existingCustomerBill = this.isCbAlreadyInTMF(cb, revenueBillId);
        logger.debug("PERSISTENCE - CB {}" , existingCustomerBill);
        
        // if not, persist it
        if(existingCustomerBill==null) {
            // FIXME: marking the CB so it can be easily removed during development. Remove before flight.
            CustomerBill cbToPersist = watermark(cb);
            // persist it
            logger.debug("PERSISTENCE: creating CB {}...", cbToPersist.getId());
            String id = this.customerBillApis.createCustomerBill(CustomerBillCreate.fromJson(cbToPersist.toJson()));
            logger.info("PERSISTENCE: created CB with id {}", id);
            // and return a fresh copy
            return this.tmfDataRetriever.getCustomerBillById(id);
//            return existingCustomerBill;
        } else {
            logger.info("Local CB {} is already on TMF with id {}", cb.getId(), existingCustomerBill.getId());
            // return null not CB
            return null;
        }
    }

    /*
	 * Persist an AppliedCustomerBillingRate if not already present on TMF. 
	 */
    public void persistAppliedCustomerBillingRate(AppliedCustomerBillingRate acbr) throws Exception {
        // check if exist on tmf
        AppliedCustomerBillingRate existingACBR = this.isAcbrAlreadyInTMF(acbr);

        // if not, persist it
        if(existingACBR==null) {
            // FIXME: marking the ACBR so it can be easily removed during development. Remove before flight.
        	AppliedCustomerBillingRate acbrToPersist = watermark(acbr);
            // remove the reference to the bill (its to an local CB)
            // acbr.setBill(null);
            // persist it
            logger.info("PERSISTENCE: creating ACBR {}", acbrToPersist.getId());

            AppliedCustomerBillingRateCreate acbrc = AppliedCustomerBillingRateCreate.fromJson(acbrToPersist.toJson());
            acbrc.setAtSchemaLocation(new URI("https://raw.githubusercontent.com/DOME-Marketplace/dome-odrl-profile/refs/heads/add-related-party-ref/schemas/simplified/RelatedPartyRef.schema.json"));
            String createdACBRId = appliedCustomerBillRateApis.createAppliedCustomerBillingRate(acbrc);

            logger.info("PERSISTENCE: created ACBR with id {}", createdACBRId);
            // and return a fresh copy
//            return createdACBR;
        } else {
            logger.info("Local ACBR {} is already on TMF with id {}", acbr.getId(), existingACBR.getId());
//            return existingACBR;
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
        // Optional filter can be set here
/*         Map<String, String> filter = new HashMap<>();
         filter.put("relatedParty.id", cb.getRelatedParty().getId());
         filter.put("billingAccount.id", "urn:ngsi-ld:billing-account:...");*/
        
    	//final CustomerBill[] found = new CustomerBill[1];
                
        // Fetch customer bills in batches with early stop
                            
/*        
        TMFApiUtils.fetchCustomerBillsByBatch(customerBillAPI, null, 10, null, batch -> {
            for (CustomerBill candidate : batch) {
                boolean basicMatch = TmfPeristenceService.match(cb, candidate);
                boolean productMatch = compareCBsProduct(revenueBillId, candidate.getId());
                boolean rlMatch = relatedPartyMatch(cb.getRelatedParty(), candidate.getRelatedParty());

                if (basicMatch && productMatch && rlMatch) {
                    found[0] = candidate;
                    return false; // stop fetching immediately
                }
            }
            return true; // continue fetching next batch
        });
*/
        //return found[0]; // null if no match found
        
        //TODO - Verify 
    	Optional<CustomerBill> found = FetchUtils.streamAll(
    		    customerBillApis::listCustomerBills,
    		    null,
    		    null,
    		    10
    		)
    		.filter(candidate -> {
    		    try {
    		        boolean basicMatch = TmfPeristenceService.match(cb, candidate);
    		        boolean productMatch = compareCBsProduct(revenueBillId, candidate.getId());
    		        boolean rlMatch = relatedPartyMatch(cb.getRelatedParty(), candidate.getRelatedParty());
    		        return basicMatch && productMatch && rlMatch;
    		    } catch (Exception e) {
    		        logger.error("Error in isCbAlreadyInTMF filter: {}", e.getMessage());
    		        return false;
    		    }
    		})
    		.findFirst();

    		return found.orElse(null);
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
        // prepare a filter
        Map<String, String> filter = new HashMap<>();
        filter.put("periodCoverage.startDateTime", acbr.getPeriodCoverage().getStartDateTime().toString());

        /*
        final AppliedCustomerBillingRate[] found = new AppliedCustomerBillingRate[1];

        TMFApiUtils.fetchAppliedCustomerBillingRateByBatch(acbrAPI, null, 10, filter, batch -> {
            for (AppliedCustomerBillingRate candidate : batch) {
                boolean basicMatch = TmfPeristenceService.match(acbr, candidate);
                // match criteria
                if (basicMatch) {
                    found[0] = candidate;
                    return false; // stop fetching immediately
                }
            }
            return true; // continue fetching next batch
        });

        return found[0]; // null if no match found
        */

        //TODO - Verify 
        Optional<AppliedCustomerBillingRate> found = FetchUtils.streamAll(
            appliedCustomerBillRateApis::listAppliedCustomerBillingRates,
            null,
            filter,
            10
        )
        .filter(candidate -> TmfPeristenceService.match(acbr, candidate))
        .findFirst();

        return found.orElse(null);
    }

    /**
     *  Compare on the following fields: billingPeriod.start/end, taxExcludedAmount.value, and if acbrs for both match
     */
    private static boolean match(CustomerBill cb1, CustomerBill cb2) {
        Map<String, String> cb1map = buildComparisonMap(cb1);
        Map<String, String> cb2map = buildComparisonMap(cb2);
        return mapsMatch(cb1map, cb2map);
    }

    /**
     *  Compare on the following fields: product, periodcoverage, name, description, type, taxExcludedAmount
     */
    private static boolean match(AppliedCustomerBillingRate acbr1, AppliedCustomerBillingRate acbr2) {
        Map<String, String> acbr1map = buildComparisonMap(acbr1);
        Map<String, String> acbr2map = buildComparisonMap(acbr2);
        return mapsMatch(acbr1map, acbr2map);
    }

    private static Map<String, String> buildComparisonMap(CustomerBill cb) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Map<String, String> out = new HashMap<>();
        if(cb.getBillingPeriod()!=null && cb.getBillingPeriod().getStartDateTime()!=null)
            out.put("billingPeriod.startDateTime", fmt.format(cb.getBillingPeriod().getStartDateTime()));
        if(cb.getBillingPeriod()!=null && cb.getBillingPeriod().getEndDateTime()!=null)
            out.put("billingPeriod.endDateTime", fmt.format(cb.getBillingPeriod().getEndDateTime()));
        if(cb.getTaxExcludedAmount()!=null && cb.getTaxExcludedAmount().getValue()!=null)
            out.put("taxExcludedAmount.value", cb.getTaxExcludedAmount().getValue().toString());
        
        return out;
    }

    private static Map<String, String> buildComparisonMap(AppliedCustomerBillingRate cb) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Map<String, String> out = new HashMap<>();
        if(cb.getProduct()!=null && cb.getProduct().getId()!=null)
            out.put("product.id", cb.getProduct().getId());
        if(cb.getPeriodCoverage()!=null && cb.getPeriodCoverage().getStartDateTime()!=null)
            out.put("periodCoverage.startDateTime", fmt.format(cb.getPeriodCoverage().getStartDateTime()));
        if(cb.getPeriodCoverage()!=null && cb.getPeriodCoverage().getEndDateTime()!=null)
            out.put("periodCoverage.endDateTime", fmt.format(cb.getPeriodCoverage().getEndDateTime()));
//        if(cb.getName()!=null)
//            out.put("name", cb.getName());
//        if(cb.getDescription()!=null)
//            out.put("description", cb.getDescription());
        if(cb.getBillingAccount()!=null && cb.getBillingAccount().getId()!=null)
        	out.put("billingAccount.id", cb.getBillingAccount().getId());
        if(cb.getType()!=null)
            out.put("type", cb.getType());
        if(cb.getTaxExcludedAmount()!=null && cb.getTaxExcludedAmount().getValue()!=null)
            out.put("taxExcludedAmount.value", cb.getTaxExcludedAmount().getValue().toString());
        return out;
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
        String mark = "Created by the Revenue Engine";
        if(acbr.getDescription()!=null) {
            acbr.setDescription(acbr.getDescription() + " - " + mark);
        } else {
            acbr.setDescription(mark);
        }
        return acbr;
    }

    private static CustomerBill watermark(CustomerBill cb) {
        String mark = "Created by the Revenue Engine";
        if(cb.getCategory()!=null) {
            cb.setCategory(cb.getCategory() + " - " + mark);
        } else {
            cb.setCategory(mark);
        }
        return cb;
    }
    
    /**
     * Compares the products of two CustomerBills by their IDs.
     * <p>
     * Retrieves the first AppliedCustomerBillingRate (ACBR) of each CustomerBill and 
     * checks if their ProductRefs are equal (id, href, name).
     * </p>
     *
     * @param idCustomerBill1 ID of the first CustomerBill
     * @param idCustomerBill2 ID of the second CustomerBill
     * @return true if both CustomerBills reference the same Product, false otherwise
    */
    private boolean compareCBsProduct(String idCustomerBill1, String idCustomerBill2) throws Exception {
    	if(idCustomerBill1 == null || idCustomerBill2 == null) {
    		logger.warn("One of the customer bill IDs is null. idCustomerBill1={}, idCustomerBill2={}", idCustomerBill1, idCustomerBill2);
    		return false;
    	}
    	
    	if(idCustomerBill1.equals(idCustomerBill2)) {
    		return true;
    	}
    	
    	List<AppliedCustomerBillingRate> acbrs1 = billService.getACBRsByRevenueBillId(idCustomerBill1); // recupera da local
    	List<AppliedCustomerBillingRate> acbrs2 = tmfDataRetriever.getACBRsByCustomerBillId(idCustomerBill2); // retrieve from TMF
    	
    	if(acbrs1 == null || acbrs2 == null || acbrs1.isEmpty() || acbrs2.isEmpty()) {
            logger.warn("NO ACBR found for at least one CB in {} {}", idCustomerBill1, idCustomerBill2);
    		return false;
    	}
        
        ProductRef productRef1 = acbrs1.get(0).getProduct();
        ProductRef productRef2 = acbrs2.get(0).getProduct();
        
        if (productRef1 == null || productRef2 == null) {
            logger.warn("One of the products is null. productRef1={}, productRef2={}", productRef1, productRef2);
            return false;
        }

        return Objects.equals(productRef1.getId(), productRef2.getId());
    }
    
    private boolean relatedPartyMatch(List<RelatedParty> rl1, List<RelatedParty> rl2) {
        String rlId1 = this.getRelatedPartyIdByRole(rl1, Role.BUYER);
        String rlId2 = this.getRelatedPartyIdByRole(rl2, Role.BUYER);

        return rlId1.equals(rlId2);
    }

    private String getRelatedPartyIdByRole(List<RelatedParty> relatedParties, Role role) {
        if (relatedParties == null || role == null) return null;

        for (RelatedParty rp : relatedParties) {
            if (rp != null && rp.getRole() != null && role.getValue().equalsIgnoreCase(rp.getRole())) {
                return rp.getId();
            }
        }
        return null;
    }

}