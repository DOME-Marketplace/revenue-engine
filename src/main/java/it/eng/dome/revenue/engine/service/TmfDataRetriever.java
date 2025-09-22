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
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Characteristic;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class TmfDataRetriever implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(TmfDataRetriever.class);

    @Autowired
    // Factory for TMF APIss
    private TmfApiFactory tmfApiFactory;

    // TMForum API to retrieve bills
    private AppliedCustomerBillRateApis billApi;
    private OrganizationApi orgApi;
    //private AccountManagementApis accountApi;
    
    // API to retrieve product
    private ProductApis productApis;

    public TmfDataRetriever() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.billApi = new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
        this.orgApi = new OrganizationApi(tmfApiFactory.getTMF632PartyManagementApiClient());
        //this.accountApi = new AccountManagementApis(tmfApiFactory.getTMF666AccountManagementApiClient());
        this.productApis = new ProductApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
        logger.info("TmfDataRetriever initialized with billApi and orgApi");
    }

    /*
    public Set<String> getSubProviders(String sellerId, TimePeriod timePeriod) {
        Set<String> subproviders = new TreeSet<String>();
        // TODO: retrieve all ACBRS by 'sellerId', in timePeriod
        // - somehow exctract who's the sub-provider
        // - if not in the ACBR, look at the Product.
        // TODISCUSS: where the sub-provider is set in the spec, offering, order, product, cb, acbr????
        return subproviders;
    }
    */

    /**
	 * Retrieves bills from the TMF API based on the provided seller ID, time period, and billing status.
	 *
	 * @param sellerId The ID of the seller to filter bills by, or null to retrieve all bills.
	 * @param timePeriod The time period within which to retrieve bills.
	 * @param isBilled If true, retrieves only billed bills; if false, retrieves only unbilled bills; if null, retrieves all.
	 * @return A list of AppliedCustomerBillingRate objects representing the retrieved bills.
	 * @throws Exception If an error occurs during retrieval.
	 */
    public List<AppliedCustomerBillingRate> retrieveBills(String sellerId, TimePeriod timePeriod, Boolean isBilled) throws Exception {
        logger.debug("Retrieving bills from TMF API between " + timePeriod.getStartDateTime() + " and " + timePeriod.getEndDateTime());

        Map<String, String> filter = new HashMap<>();

        // Add isBilled filter if specified
        if (isBilled != null) {
            filter.put("isBilled", isBilled.toString());
        }

        // Add time period filters if available
        if (timePeriod.getStartDateTime() != null) {
            filter.put("date.gt", timePeriod.getStartDateTime().toString());
        }
        if (timePeriod.getEndDateTime() != null) {
            filter.put("date.lt", timePeriod.getEndDateTime().toString());
        }

        // FIXME: be careful not to match attributes across different relatedParties
        if (sellerId != null) {
            filter.put("relatedParty", sellerId);
            filter.put("relatedParty.role", "Seller");
            logger.debug("Retrieving bills for seller with id: " + sellerId);
        } else {
            logger.debug("Retrieving all bills in the specified period");
        }

        List<AppliedCustomerBillingRate> out = billApi.getAllAppliedCustomerBillingRates(null, filter);

        // TODO: further filter results to be sure id and role are in the sameRelatedParties (see fixme above)

        // TODO: further filters, if any (maybe by using a map of properties in the signature)

        logger.debug("Found " + out.size() + " bills in the specified period");
        return out;
    }

    /**
	 * Retrieves active sellers from the TMF API based on the provided time period.
	 * 
	 * @param timePeriod The time period within which to retrieve active sellers.
	 * @return A list of Organization objects representing the active sellers.
	 * @throws Exception If an error occurs during retrieval.
	*/
    public List<Organization> retrieveActiveSellers(TimePeriod timePeriod) throws Exception {

        logger.info("Retrieving active sellers from TMF API between " + timePeriod.getStartDateTime() + " and " + timePeriod.getEndDateTime());

        // id of sellers from bills
        Set<String> sellersIds = new TreeSet<>(); 

        // outuput, the organisations retrieved from the API
        List<Organization> activeSellers = new ArrayList<>();

        // prepare the filter: only billed bills and in the given period
        Map<String, String> filter = new HashMap<>();
        filter.put("isBilled", "true");
        if(timePeriod.getStartDateTime()!=null)
            filter.put("date.gt", timePeriod.getStartDateTime().toString());
        if(timePeriod.getEndDateTime()!=null)
            filter.put("date.lt", timePeriod.getEndDateTime().toString());

        // retrieve bills and extract seller ids
        List<AppliedCustomerBillingRate> bills = this.retrieveBills(null, timePeriod, true);
        for(AppliedCustomerBillingRate acbr: bills) {
            if(acbr==null || acbr.getRelatedParty()==null)
                continue;
            for(it.eng.dome.tmforum.tmf678.v4.model.RelatedParty rp: acbr.getRelatedParty()) {
                if("Seller".equals(rp.getRole())) {
                    sellersIds.add(rp.getId());
                }
            }
        }

        // retrieve the organisations
        for(String s:sellersIds) {
            logger.debug("Retrieving organisation with id: " + s);
            try {
                Organization org = orgApi.retrieveOrganization(s, null);
                if(org!=null) {
                    logger.debug(org.getTradingName() + " " + org.getName() + " " + org.getId());
                    activeSellers.add(org);
                }
            } catch(Exception e) {
                logger.error("unable to retrieve organisation with id " + s + " appearing as seller");
                logger.error("", e);
            }
        }

        return activeSellers;
    }
    
    /**
	 * Lists all organizations that have been referred by a specific referrer organization.
	 *
	 * @param referrerOrganizationId The ID of the organization that referred others.
	 * @return A list of Organization objects representing the referred organizations.
	 * @throws Exception If an error occurs during retrieval.
	*/
    public List<Organization> listReferralsProviders(String referrerOrganizationId) throws Exception {
    	logger.info("List referrals for referrerOrganizationId {}", referrerOrganizationId);
    	try {
    		
            // outuput, the the referred organizations
            List<Organization> referrals = new ArrayList<>();

            // prepare the filter: only billed bills and in the given period
            Map<String, String> filter = new HashMap<>();
            filter.put("partyCharacteristic.name", "referredBy");

            // retrieve organizations with the 'referredBy' field
            List<Organization> orgs = orgApi.listOrganization(null, null, 10, filter);

            // return those matching the 'referrerOrganizationId'
            for(Organization o:orgs) {
                if(o.getPartyCharacteristic()!=null && o.getPartyCharacteristic().stream()
                        .anyMatch(pc -> "referredBy".equals(pc.getName()) && referrerOrganizationId.equals(pc.getValue()))) {
                    referrals.add(o);
                }
            }
            return referrals;
        } catch (Exception e) {
            logger.error("Error retrieving referrals for referrer provider ID: " + referrerOrganizationId, e);
            throw(e);
        }
    }
    
    /**
	 * Retrieves the referrer organization for a given referral organization ID.
	 *
	 * @param referralOrganizationId The ID of the referral organization.
	 * @return The Organization object representing the referrer, or null if not found.
	 * @throws Exception If an error occurs during retrieval.
	*/
    public Organization getReferrerProvider(String referralOrganizationId) throws Exception{
    	logger.info("Get referrer for Organization with ID {}", referralOrganizationId);
    	try {
            // get the id of the refferrer organization, if any
            Organization referralOrg = orgApi.retrieveOrganization(referralOrganizationId, null);
            // retrieve the referrefer organization from the referral organization
            if(referralOrg!=null && referralOrg.getPartyCharacteristic()!=null) {
                // find the 'referredBy' characteristic
                String referrerId = null;
                for(Characteristic pc : referralOrg.getPartyCharacteristic()) {
                    if("referredBy".equals(pc.getName())) {
                        referrerId = (String)pc.getValue();
                        break;
                    }
                }
                // if found, retrieve the organization
                if(referrerId!=null) {
                    return orgApi.retrieveOrganization(referrerId, null);
                }
            }
            // otherwise, return null
            return null;
        } catch (Exception e) {
            logger.error("Error retrieving referrer provider for referral provider ID: " + referralOrganizationId, e);
            throw(e);
        }
    }

    public List<Organization> retrieveActiveSellersInLastMonth() throws Exception {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.plusMonths(-1);
        TimePeriod timePeriod = new TimePeriod();
        timePeriod.setStartDateTime(from);
        timePeriod.setEndDateTime(to);
        return this.retrieveActiveSellers(timePeriod);
    }

    public List<AppliedCustomerBillingRate> retrieveBillsForSellerInLastMonth(String sellerId) throws Exception {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.plusMonths(-1);
        TimePeriod timePeriod = new TimePeriod();
        timePeriod.setStartDateTime(from);
        timePeriod.setEndDateTime(to);
        return this.retrieveBills(sellerId, timePeriod, true);
    }

    public List<AppliedCustomerBillingRate> retrieveBillsInLastMonth() throws Exception {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.plusMonths(-1);
        TimePeriod timePeriod = new TimePeriod();
        timePeriod.setStartDateTime(from);
        timePeriod.setEndDateTime(to);
        return this.retrieveBills(null, timePeriod, true);
    }
    
	/** * Retrieves a billing account by product ID from the TMF API.
	 *
	 * @param id The ID of the product that should contain a Billing Account.
	 * @return A BillingAccountRef object representing the billing account, or null if not found.
	*/
	public BillingAccountRef retrieveBillingAccountByProductId(String productId) {
		logger.debug("Retrieving Billing Account from TMF API By Product with id: {}" + productId);
	  
		if (productId == null) {
			logger.warn("Product ID is null, cannot retrieve billing account.");
			return null;
		}
	  
		Product prod = this.productApis.getProduct(productId, null);

		BillingAccountRef ba = prod.getBillingAccount();
	  
		if(ba == null ) {
			logger.info("No billing accounts found for product with id {}: " + productId);
			return null;
		}
	      
		return ba;
	}
	
	/** * Retrieves a billing account by related party ID from the TMF API.
	 *
	 * @param relatedPartyId The ID of the related party to filter billing accounts by.
	 * @return A BillingAccountRef object representing the billing account, or null if not found.
	*/
//    public BillingAccountRef retrieveBillingAccountByRelatedPartyId(String relatedPartyId) {
//        logger.debug("Retrieving Billing Account from TMF API By RelatedParty with id: " + relatedPartyId);
//        
//        if (relatedPartyId == null) {
//                logger.warn("RelatedParty ID is null, cannot retrieve billing account.");
//                return null;
//        }
//        
//        try {
//            // filter
//            Map<String, String> filter = new HashMap<>();
//            filter.put("relatedParty.id", relatedPartyId);
//
//            List<BillingAccount> billAccs = accountApi.getAllBillingAccounts(null, filter);
//            
//            if (billAccs == null || billAccs.isEmpty()) {
//                logger.info("No billing accounts found for related party: " + relatedPartyId);
//                return null;
//            }
//
//            BillingAccount first = billAccs.get(0);
//
//            BillingAccountRef ref = new BillingAccountRef();
//            ref.setId(first.getId());
//            URI hrefURI = first.getHref();
//            if (hrefURI != null) {
//                ref.setHref(hrefURI.toString());
//            }
//            ref.setName(first.getName());
//            ref.setAtReferredType("BillingAccount");
//
//            return ref;
//
//        } catch (Exception e) {
//            logger.error("Error retrieving billing account for related party: " + relatedPartyId, e);
//            return null;
//        }
//    }
}

