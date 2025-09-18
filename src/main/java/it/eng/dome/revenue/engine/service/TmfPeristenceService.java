package it.eng.dome.revenue.engine.service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.revenue.engine.model.RevenueBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf678.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.api.CustomerBillApi;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRateCreate;
import it.eng.dome.tmforum.tmf678.v4.model.BillRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBillCreate;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;

/**
 * FIXME: Enhancemets and fixes:
 * [L] properly manage limits
 * [M] comparison of CB should consider also attached acbrs
 * [H] only consider past & consolidated entities (cb and acbrs). Filter here based on dates, state, etc...
 * [H] only consider active subscriptions ([L] but be careful with last invoices... sub might not be active)
 */

@Service
public class TmfPeristenceService implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(TmfPeristenceService.class);

    @Autowired
    private TmfApiFactory tmfApiFactory;

    @Autowired
    private BillsService billService;

    @Autowired
    private CachedSubscriptionService subscriptionService;

    private CustomerBillApi customerBillAPI;
	private AppliedCustomerBillRateApis appliedCustomerBillRateApis;
    private OrganizationApi orgApi;

	public TmfPeristenceService() {}
    
    @Override
    public void afterPropertiesSet() throws Exception {
		this.customerBillAPI = new CustomerBillApi(tmfApiFactory.getTMF678CustomerBillApiClient());
        logger.info("TmfPeristenceService initialized with CustomerBillApi {}", this.customerBillAPI);

		this.appliedCustomerBillRateApis = new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
        logger.info("TmfPeristenceService initialized with AppliedCustomerBillRateApis {}", this.appliedCustomerBillRateApis);

        this.orgApi = new OrganizationApi(tmfApiFactory.getTMF632PartyManagementApiClient());
        logger.info("TmfPeristenceService initialized with OrganizationApi {}", this.orgApi);
    }

    /**
     * Persist all revenue bills that needs to.
     */
    public List<CustomerBill> persistAllRevenueBills() throws ApiException, Exception {
        // iterate over providers in tmforum
        // FIXME: replace limit with proper paging management (while, offset check size equals limit)
        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        for(Organization organization : orgApi.listOrganization(null, null, null, null)) {
            createdCustomerBills.addAll(this.persistProviderRevenueBills(organization.getId()));
        }
        return createdCustomerBills;
    }

    /**
     * Persist all revenue bills for a provider; where needed and applicable.
     * @param providerId
     */
    public List<CustomerBill> persistProviderRevenueBills(String providerId) throws ApiException, Exception {
        // iterate over subscriptions for the given provider
        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        for(Subscription sub: this.subscriptionService.getSubscriptionsByRelatedPartyId(providerId, "Buyer")) {
            createdCustomerBills.addAll(this.persistSubscriptionRevenueBills(sub.getId()));
        }
        return createdCustomerBills;
    }

    // Return a list of created or existing customerbills
    public List<CustomerBill> persistSubscriptionRevenueBills(String subscriptionId) throws ApiException, Exception {
        // iterate over revenue bills for the given subscription
        List<CustomerBill> createdCustomerBills = new ArrayList<>();
        for(RevenueBill sb: this.billService.getSubscriptionBills(subscriptionId)) {
            CustomerBill createdCustomerBill = this.persistRevenueBill(sb.getId());
            if(createdCustomerBill!=null) {
                createdCustomerBills.add(createdCustomerBill);
            } else {
                logger.info("CB {} was not created", sb.getId());
            }
        }
        return createdCustomerBills;
    }

    public CustomerBill persistRevenueBill(String revenueBillId) throws ApiException, Exception {

        logger.info("PERSISTENCE - persisting revenue bill {}", revenueBillId);

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

    public CustomerBill persistCustomerBill(CustomerBill cb, String revenueBillId) throws ApiException, Exception {
        // check if exist on tmf
        logger.debug("PERSISTENCE - look for existing CB");
        CustomerBill existingCustomerBill = this.isCbAlreadyInTMF(cb, revenueBillId);
        logger.debug("PERSISTENCE - CB {}" , existingCustomerBill);
        
        // if not, persist it
        if(existingCustomerBill==null) {
            // FIXME: marking the CB so it can be easily removed during development. Remove before flight.
            cb = watermark(cb);
            // persist it
            logger.debug("PERSISTENCE: creating CB {}...", cb.getId());
            String id = this.appliedCustomerBillRateApis.createCustomerBill(CustomerBillCreate.fromJson(cb.toJson()));
            logger.info("PERSISTENCE: created CB with id {}", id);
            // and return a fresh copy
            return this.customerBillAPI.retrieveCustomerBill(id, null);
//            return existingCustomerBill;
        } else {
            logger.info("Local CB {} is already on TMF with id {}", cb.getId(), existingCustomerBill.getId());
            // return null not CB
            return null;
        }
    }

    public AppliedCustomerBillingRate persistAppliedCustomerBillingRate(AppliedCustomerBillingRate acbr) throws ApiException, Exception {
        // check if exist on tmf
        AppliedCustomerBillingRate existingACBR = this.isAcbrAlreadyInTMF(acbr);

        // if not, persist it
        if(existingACBR==null) {
            // FIXME: marking the ACBR so it can be easily removed during development. Remove before flight.
            acbr = watermark(acbr);
            // remove the reference to the bill (its to an local CB)
            // acbr.setBill(null);
            // persist it
            logger.info("PERSISTENCE: creating ACBR {}", acbr.getId());
            
            AppliedCustomerBillingRateCreate acbrc = AppliedCustomerBillingRateCreate.fromJson(acbr.toJson());
            acbrc.setAtSchemaLocation(new URI("https://raw.githubusercontent.com/DOME-Marketplace/dome-odrl-profile/refs/heads/add-related-party-ref/schemas/simplified/RelatedPartyRef.schema.json"));
            AppliedCustomerBillingRate createdACBR = this.appliedCustomerBillRateApis.createAppliedCustomerBillingRate(acbrc);
            
            logger.info("PERSISTENCE: created ACBR with id {}", createdACBR.getId());
            // and return a fresh copy
            return createdACBR;
        } else {
            logger.info("Local ACBR {} is already on TMF with id {}", acbr.getId(), existingACBR.getId());
            return existingACBR;
        }
    }

    /**
     * Retrieve CBs on TMF that, potentially match the local CB. We can't use the id, as we do not know.
     * It would be useful to query by related parties (e.g. with role and id) but the query feature for tmf may match across different parties.
     * Also, querying by amount.value gives an internal server error.
     * So, we're querying by some date which should be safe and efficient.
     * However, we'll filter locally on the returned set.
     * @param cb
     * @return
     * @throws ApiException
     * @throws Exception
     */
    private CustomerBill isCbAlreadyInTMF(CustomerBill cb, String revenueBillId) throws ApiException, Exception {

        // prepare a filter
//        Map<String, String> filter = new HashMap<>();
//        filter.put("relatedParty.id", cb.getRelatedParty().getId())
//        filter.put("billingAccount.id", "urn:ngsi-ld:billing-account:d8f73230-fe44-4594-9cc0-6c2b76107187");
        
        // retrieve matches (shouldn't be a large set)
        // FIXME: properly manage limit and paging
        List<CustomerBill> candidates = this.customerBillAPI.listCustomerBill(null, null, 1000, null);
        logger.info("PERSISTENCE - FOUND {} cb matching initial criteria", candidates.size());

        // compare candidates with the local CustomerBill
        List<CustomerBill> matchedCandidates = new ArrayList<>();
        for(CustomerBill candidate: candidates) {
        	boolean basicMatch = TmfPeristenceService.match(cb, candidate);
            boolean productMatch = this.compareCBsProduct(revenueBillId, candidate.getId());
            boolean rlMatch = this.relatedPartyMatch(cb.getRelatedParty(), candidate.getRelatedParty());

            //&& productMatch
            if (basicMatch && productMatch && rlMatch) {
                matchedCandidates.add(candidate);
            }
//            else {
//                logger.debug("isCbAlreadyInTMF - Candidate {} discarded. basicMatch={}, productMatch={}", 
//                             candidate.getId(), basicMatch, productMatch);
//            }
        }

        // ok if there's one match. null if no match. Exception if more matches (non).
        if(matchedCandidates.size()==1) {
        	logger.debug("**********CB machedCandidates size is 1");
            return matchedCandidates.get(0);
        }
        else if(matchedCandidates.isEmpty()) {
        	logger.debug("******CB machedCandidates is Empty");
            return null;
        }
        else {
            //throw new Exception(String.format("Found {} CustomerBills already on TMF matching the given CustomerBill with local id {}", matchedCandidates.size(), cb.getId()));
            String msg = String.format("Found %d CustomerBills already on TMF matching the given CustomerBill with local id %s", matchedCandidates.size(), cb.getId());
            logger.error(msg);
            return matchedCandidates.get(0);
        }
    }

    private AppliedCustomerBillingRate isAcbrAlreadyInTMF(AppliedCustomerBillingRate acbr) throws ApiException, Exception {
        // prepare a filter
        Map<String, String> filter = new HashMap<>();
        filter.put("periodCoverage.startDateTime", acbr.getPeriodCoverage().getStartDateTime().toString());

        // retrieve matches (shouldn't be a large set)
        List<AppliedCustomerBillingRate> candidates = this.appliedCustomerBillRateApis.getAllAppliedCustomerBillingRates(null, filter);

        // compare candidates with the local CustomerBill
        List<AppliedCustomerBillingRate> matches = new ArrayList<>();
        for(AppliedCustomerBillingRate candidate: candidates) {
            if(TmfPeristenceService.match(acbr, candidate))
                matches.add(candidate);
        }

        // ok if there's one match. null if no match. Exception if more matches.
        if(matches.size()==1) {
        	logger.debug("&&&&&&&&&& ACBR matches size is 1");
            return matches.get(0);
        }
        else if(matches.isEmpty()) {
        	logger.debug("&&&&&&& ACBR matches is Empty");
            return null;
        }
        else {
            throw new Exception(String.format("Found {} AppliedCustomerBillingRates already on TMF matching the given AppliedCustomerBillingRate with local id {}", matches.size(), acbr.getId()));
        }    
    }

    /**
     *  Compare on the following fields: billingPeriod.start/end, taxExcludedAmount.value, and if acbrs for both match
     */
    private static boolean match(CustomerBill cb1, CustomerBill cb2) {
        Map<String, String> cb1map = buildComparisonMap(cb1);
        Map<String, String> cb2map = buildComparisonMap(cb2);
        // FIXME: also compare corrsponding acbrs (as there is no product in cb)
        // [DZCF] - A: done above in isCBAlreadyinTMF() with compareCBsProduct() fuction
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
    private boolean compareCBsProduct(String idCustomerBill1, String idCustomerBill2) {
    	if(idCustomerBill1 == null || idCustomerBill2 == null) {
    		logger.warn("One of the customer bill IDs is null. idCustomerBill1={}, idCustomerBill2={}", idCustomerBill1, idCustomerBill2);
    		return false;
    	}
    	
    	if(idCustomerBill1.equals(idCustomerBill2)) {
    		return true;
    	}
    	
    	List<AppliedCustomerBillingRate> acbrs1 = billService.getACBRsByRevenueBillId(idCustomerBill1); // recupera da local
    	List<AppliedCustomerBillingRate> acbrs2 = billService.getACBRsByCustomerBillId(idCustomerBill2);
    	
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
        
        boolean areEqual = Objects.equals(productRef1.getId(), productRef2.getId());
        
        return areEqual;
    }
    
    private boolean relatedPartyMatch(List<RelatedParty> rl1, List<RelatedParty> rl2) {
    	String rlId1 = null;
    	String rlId2 = null;
		if(rl1 != null && this.filterRelatedPartyPerRole(rl1, "Buyer")!=null)
			rlId1 = this.filterRelatedPartyPerRole(rl1, "Buyer").getId();
		if(rl2 != null && this.filterRelatedPartyPerRole(rl2, "Buyer")!=null)
			rlId2 = this.filterRelatedPartyPerRole(rl2, "Buyer").getId();
    	
		if(rlId1!=null && rlId2!=null && rlId1.equals(rlId2))
			return true;
		else
			return false;
    }
    
    public RelatedParty filterRelatedPartyPerRole(List<RelatedParty> relatedParties, String role) {
    	for(RelatedParty rl : relatedParties) {
    		if(role.equalsIgnoreCase(rl.getRole())){
    			return rl;
    		}
    	}
    	return null;
    }
}
