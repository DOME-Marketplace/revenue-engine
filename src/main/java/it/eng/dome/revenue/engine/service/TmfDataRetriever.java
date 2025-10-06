package it.eng.dome.revenue.engine.service;

import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.ProductApis;
import it.eng.dome.brokerage.api.ProductOfferingApis;
import it.eng.dome.brokerage.api.ProductOfferingPriceApis;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.revenue.engine.utils.TMFApiUtils;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Characteristic;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TmfDataRetriever implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(TmfDataRetriever.class);

    // Factory for TMF APIss
    @Autowired
    private TmfApiFactory tmfApiFactory;

    // TMForum API to retrieve bills, organizations and products
    private AppliedCustomerBillRateApis billApi;
    private OrganizationApi orgApi;
    private ProductApis productApis;
    private ProductOfferingApis productOfferingApis;
    private ProductOfferingPriceApis popApis;

    public TmfDataRetriever() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.billApi = new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
        this.orgApi = new OrganizationApi(tmfApiFactory.getTMF632PartyManagementApiClient());
        this.productApis = new ProductApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
        this.productOfferingApis = new ProductOfferingApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient());
        this.popApis = new ProductOfferingPriceApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient());
        logger.info("TmfDataRetriever initialized with the following api: {}, {}, {}, {}, {}", billApi, productApis, orgApi, productOfferingApis, popApis);
    }


    // ======= TMF Customer Bill ========

    public CustomerBill getCustomerBillById(String customerBillId) {
        logger.debug("Retrieving Customer Bill from TMF API By Customer Bill with id: {}", customerBillId);

        if (customerBillId == null) {
            logger.warn("Customer Bill ID is null, cannot retrieve Customer Bill.");
            return null;
        }

        CustomerBill cb = this.billApi.getCustomerBill(customerBillId, null);

        if(cb == null ) {
            logger.info("No Customer Bill found for Customer Bill with id {}: ", customerBillId);
            return null;
        }

        return cb;
    }
    
    public List<CustomerBill> getAllCustomerBills(String fields, Map<String, String> filter) {

		return this.billApi.getAllCustomerBills(fields, filter);
	}


    // ======== TMF ACBRs ========

    public List<AppliedCustomerBillingRate> getACBRsByCustomerBillId (String customerBillId) {
        logger.info("Retrieving AppliedCustomerBillingRate from TMF API By Customer Bill with id: {}", customerBillId);

        if (customerBillId == null) {
            logger.warn("Customer Bill ID is null, cannot retrieve AppliedCustomerBillingRate.");
            return Collections.emptyList();
        }

        Map<String, String> filter = new HashMap<>();
        filter.put("bill.id", customerBillId);

        List<AppliedCustomerBillingRate> acbrs = billApi.getAllAppliedCustomerBillingRates(null, filter);

        if (acbrs == null || acbrs.isEmpty()) {
            logger.info("No AppliedCustomerBillingRate found for Customer Bill with id {}.", customerBillId);
            return Collections.emptyList();
        }

        return acbrs;
    }

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
        logger.debug("Retrieving bills from TMF API between {} and {}", timePeriod.getStartDateTime(), timePeriod.getEndDateTime());

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

        // --> FIX ME: be careful not to match attributes across different relatedParties
        if (sellerId != null) {
            filter.put("relatedParty.id", sellerId);
            filter.put("relatedParty.role", "Seller");
            logger.debug("Retrieving bills for seller with id: {}", sellerId);
        } else {
            logger.debug("Retrieving all bills in the specified period");
        }

        List<AppliedCustomerBillingRate> out = billApi.getAllAppliedCustomerBillingRates(null, filter);

        // DONE --> further filter results to be sure id and role are in the sameRelatedParties (see fix me above)
        // Filter to ensure same RelatedParty has both id and role
        List<AppliedCustomerBillingRate> filtered = out.stream()
                .filter(bill -> bill.getRelatedParty() != null && bill.getRelatedParty().stream()
                                .anyMatch(rp -> sellerId == null || (sellerId.equals(rp.getId()) && "Seller".equalsIgnoreCase(rp.getRole())))
                )
                .collect(Collectors.toList());

        // TODO: further filters, if any (maybe by using a map of properties in the signature)

        logger.debug("Found {} bills in the specified period after role/id filter", filtered.size());
        return filtered;
    }


    // ======== TMF ORGANIZATIONS ========

    /**
	 * Retrieves active sellers from the TMF API based on the provided time period.
	 * 
	 * @param timePeriod The time period within which to retrieve active sellers.
	 * @return A list of Organization objects representing the active sellers.
	 * @throws Exception If an error occurs during retrieval.
	*/
    public List<Organization> retrieveActiveSellers(TimePeriod timePeriod) throws Exception {

        logger.info("Retrieving active sellers from TMF API between {} and {}", timePeriod.getStartDateTime(), timePeriod.getEndDateTime());

        // id of sellers from bills
        Set<String> sellersIds = new TreeSet<>(); 

        // outuput, the organisations retrieved from the API
        List<Organization> activeSellers = new ArrayList<>();

        // prepare the filter: only billed bills and in the given period
//        Map<String, String> filter = new HashMap<>();
//        filter.put("isBilled", "true");
//        if(timePeriod.getStartDateTime()!=null)
//            filter.put("date.gt", timePeriod.getStartDateTime().toString());
//        if(timePeriod.getEndDateTime()!=null)
//            filter.put("date.lt", timePeriod.getEndDateTime().toString());

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
            logger.debug("Retrieving organisation with id: {}", s);
            try {
                Organization org = orgApi.retrieveOrganization(s, null);
                if(org!=null) {
                    logger.debug("{} {} {}", org.getTradingName(), org.getName(), org.getId());
                    activeSellers.add(org);
                }
            } catch(Exception e) {
                logger.error("unable to retrieve organisation with id {} appearing as seller", s);
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
            logger.error("Error retrieving referrals for referrer provider ID: {}", referrerOrganizationId, e);
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
            logger.error("Error retrieving referrer provider for referral provider ID: {}", referralOrganizationId, e);
            throw(e);
        }
    }

    public List<Organization> getAllPaginatedOrg() throws Exception {
        logger.info("Retrieving all organizations from TMF API");
        // Optional filter can be set here
        // Map<String, String> filter = new HashMap<>();
        // filter.put("partyCharacteristic.name", "country");
        // Fetch all organizations using the new TMFApiUtils method
        List<Organization> allOrgs = TMFApiUtils.fetchAllOrganizations(orgApi, null, 20, null);
        logger.info("Retrieved {} organizations from TMF API", allOrgs.size());
        return allOrgs;
    }

    /*public List<Organization> retrieveActiveSellersInLastMonth() throws Exception {
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
    }*/


    // ======== TMF BILLING ACCOUNTS ========
    
	/** * Retrieves a billing account by product ID from the TMF API.
	 *
	 * @param productId The ID of the product that should contain a Billing Account.
	 * @return A BillingAccountRef object representing the billing account, or null if not found.
	*/
	public BillingAccountRef retrieveBillingAccountByProductId(String productId) {
		logger.debug("Retrieving Billing Account from TMF API By Product with id: {}", productId);
	  
		if (productId == null) {
			logger.warn("Product ID is null, cannot retrieve billing account.");
			return null;
		}
	  
		Product prod = this.productApis.getProduct(productId, null);

		BillingAccountRef ba = prod.getBillingAccount();
	  
		if(ba == null ) {
			logger.info("No billing accounts found for product with id {}: ", productId);
			return null;
		}
	      
		return ba;
	}

    // ======== TMF PRODUCTS ========
    public Product getProductById(String productId, String fields) {
        //logger.debug("Retrieving Product from TMF API By Product with id: {}", productId);

        if (productId == null) {
            logger.warn("Product ID is null, cannot retrieve product.");
            return null;
        }

        Product prod = this.productApis.getProduct(productId, fields);

        if(prod == null ) {
            logger.info("No product found for product with id {}: ", productId);
            return null;
        }

        return prod;
    }

    public List<Product> getAllProducts(String fields, Map<String, String> filter) {
        logger.debug("Retrieving all Products from TMF API");

        return this.productApis.getAllProducts(fields, filter);
    }

    // ======== PRODUCT OFFERING ========

    public ProductOffering getProductOfferingById(String poId, String fields) {
        logger.debug("Retrieving Product Offering from TMF API By Product Offering with id: {}", poId);

        if (poId == null) {
            logger.warn("Product Offering ID is null, cannot retrieve product offering.");
            return null;
        }

        return this.productOfferingApis.getProductOffering(poId, fields);
    }

    public List<ProductOffering> getAllProductOfferings(String fields, Map<String, String> filter) {
        logger.debug("Retrieving all Product Offerings from TMF API");

        return this.productOfferingApis.getAllProductOfferings(fields, filter);
    }

    // ======== PRODUCT OFFERING PRICE ========
    public ProductOfferingPrice getProductOfferingPrice(String popId, String fields) {
//        logger.debug("Retrieving Product Offering Price from TMF API By Product Offering Price with id: {}", popId);

        if (popId == null) {
            logger.warn("Product Offering Price ID is null, cannot retrieve product offering price.");
            return null;
        }

        return this.popApis.getProductOfferingPrice(popId, fields);
    }
}

