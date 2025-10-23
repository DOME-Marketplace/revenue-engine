package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import it.eng.dome.brokerage.api.*;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.revenue.engine.utils.RelatedPartyUtils;
import it.eng.dome.revenue.engine.utils.TMFApiUtils;
import it.eng.dome.tmforum.tmf620.v4.model.Category;
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

@Service
public class TmfDataRetriever implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(TmfDataRetriever.class);

    // Factory for TMF APIss
    @Autowired
    private TmfApiFactory tmfApiFactory;

    // TMForum API to retrieve bills, organizations and products
    private AppliedCustomerBillRateApis acbrApi;
    private CustomerBillApis customerBillApi;
    private APIPartyApis apiPartyApis;
    private ProductInventoryApis productApis;
    private ProductCatalogManagementApis productCatalogManagementApis;

    public TmfDataRetriever() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.acbrApi = new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
        this.apiPartyApis = new APIPartyApis(tmfApiFactory.getTMF632PartyManagementApiClient());
        this.productApis = new ProductInventoryApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
        this.productCatalogManagementApis = new ProductCatalogManagementApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient());
        this.customerBillApi = new CustomerBillApis(tmfApiFactory.getTMF678CustomerBillApiClient());
        logger.info("TmfDataRetriever initialized with the following api: {}, {}, {}, {}, {}", acbrApi, customerBillApi, productApis, apiPartyApis, productCatalogManagementApis);
    }

    // ======= TMF Customer Bill ========

    public CustomerBill getCustomerBillById(String customerBillId)
            throws BadTmfDataException, ExternalServiceException {
        logger.debug("Retrieving Customer Bill from TMF API By Customer Bill with id: {}", customerBillId);

        if (customerBillId == null) {
            throw new BadTmfDataException("CustomerBill", customerBillId, "Customer Bill ID cannot be null");
        }

        try {
            CustomerBill cb = this.customerBillApi.getCustomerBill(customerBillId, null);

            if (cb == null) {
                logger.info("No Customer Bill found for Customer Bill with id {}: ", customerBillId);
                return null;
            }

            return cb;
        } catch (Exception e) {
            logger.error("Error retrieving Customer Bill {}: {}", customerBillId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve Customer Bill with ID: " + customerBillId, e);
        }
    }

    public List<CustomerBill> getAllCustomerBills(String fields, Map<String, String> filter, int pageSize)
            throws ExternalServiceException {
        try {
            //FIXME: fix retrieve of large CustomerBill lists
            return FetchUtils.streamAll(
                    customerBillApi::listCustomerBills,    // method reference
                    fields,                       		   // fields
                    filter,            					   // filter
                    pageSize                         	   // pageSize
            ).toList();
        } catch (Exception e) {
            logger.error("Error retrieving all customer bills", e);
            throw new ExternalServiceException("Failed to retrieve all customer bills", e);
        }
    }

    // ======== TMF ACBRs ========

    public List<AppliedCustomerBillingRate> getACBRsByCustomerBillId(String customerBillId)
            throws BadTmfDataException, ExternalServiceException {
        logger.info("Retrieving AppliedCustomerBillingRate from TMF API By Customer Bill with id: {}", customerBillId);

        if (customerBillId == null) {
            throw new BadTmfDataException("CustomerBill", customerBillId, "Customer Bill ID cannot be null");
        }

        try {
            Map<String, String> filter = new HashMap<>();
            filter.put("bill.id", customerBillId);
            //FIXME: fix retrieve of large ACBR lists
            List<AppliedCustomerBillingRate> acbrs = FetchUtils.streamAll(
                    acbrApi::listAppliedCustomerBillingRates,    // method reference
                    null,                       		   // fields
                    filter,            					   // filter
                    100                         	       // pageSize
            ).toList();

            if (acbrs == null || acbrs.isEmpty()) {
                logger.info("No AppliedCustomerBillingRate found for Customer Bill with id {}.", customerBillId);
                return Collections.emptyList();
            }

            return acbrs;
        } catch (Exception e) {
            logger.error("Error retrieving ACBRs for Customer Bill {}: {}", customerBillId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve ACBRs for Customer Bill with ID: " + customerBillId, e);
        }
    }

    /**
     * Retrieves bills from the TMF API based on the provided seller ID, time period, and billing status.
     *
     * @param sellerId The ID of the seller to filter bills by, or null to retrieve all bills.
     * @param timePeriod The time period within which to retrieve bills.
     * @return A list of AppliedCustomerBillingRate objects representing the retrieved bills.
     * @throws ExternalServiceException If an error occurs during retrieval.
     */
    public List<CustomerBill> retrieveBills(String sellerId, TimePeriod timePeriod)
            throws ExternalServiceException {
        try {
            logger.debug("Retrieving bills from TMF API between {} and {}", timePeriod.getStartDateTime(), timePeriod.getEndDateTime());

            Map<String, String> filter = new HashMap<>();

            if (timePeriod.getStartDateTime() != null) {
                filter.put("billDate.gt", timePeriod.getStartDateTime().toString());
            }
            if (timePeriod.getEndDateTime() != null) {
                filter.put("billDate.lt", timePeriod.getEndDateTime().toString());
            }

            if (sellerId != null) {
                filter.put("relatedParty.id", sellerId);
                filter.put("relatedParty.role", "Seller");
                logger.debug("Retrieving bills for seller with id: {}", sellerId);
            } else {
                logger.debug("Retrieving all bills in the specified period");
            }

            //FIXME: fix retrieve of large CustomerBill lists
            List<CustomerBill> out = FetchUtils.streamAll(
                    customerBillApi::listCustomerBills,    // method reference
                    null,                       		   // fields
                    filter,            					   // filter
                    100                         	   // pageSize
            ).toList();

            List<CustomerBill> filtered = new ArrayList<>();
            if (sellerId != null) {
                filtered = RelatedPartyUtils.retainCustomerBillsWithParty(out, sellerId, Role.SELLER);
            }

            logger.debug("Found {} bills in the specified period after role/id filter", filtered.size());
            return filtered;
        } catch (Exception e) {
            logger.error("Failed to retrieve bills for seller {}: {}", sellerId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve bills for seller ID: " + sellerId, e);
        }
    }

    private List<CustomerBill> retrieveBills(String participantId, Role participantRole, TimePeriod timePeriod)
            throws ExternalServiceException {
        try {
            logger.debug("Retrieving bills from TMF API between {} and {}", timePeriod.getStartDateTime(), timePeriod.getEndDateTime());

            Map<String, String> filter = new HashMap<>();

            if (timePeriod.getStartDateTime() != null) {
                filter.put("billDate.gt", timePeriod.getStartDateTime().toString());
            }
            if (timePeriod.getEndDateTime() != null) {
                filter.put("billDate.lt", timePeriod.getEndDateTime().toString());
            }

            if (participantId != null) {
                filter.put("relatedParty.id", participantId);
                logger.debug("Retrieving bills for participant with id: {}", participantId);
            }
            if (participantRole != null) {
                filter.put("relatedParty.role", participantRole.getValue());
                logger.debug("Retrieving bills for participant with role: {}", participantRole);
            }

            //FIXME: fix retrieve of large CustomerBill lists
            List<CustomerBill> out = FetchUtils.streamAll(
                    customerBillApi::listCustomerBills,    // method reference
                    null,                       		   // fields
                    filter,            					   // filter
                    100                         	   // pageSize
            ).toList();
            out = RelatedPartyUtils.retainCustomerBillsWithParty(out, participantId, participantRole);

            logger.debug("Found {} bills in the specified period after role/id filter", out.size());
            return out;
        } catch (Exception e) {
            logger.error("Failed to retrieve bills for participant {} with role {}: {}", participantId, participantRole, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve bills for participant ID: " + participantId, e);
        }
    }

    // ======== TMF ORGANIZATIONS ========

    public List<Organization> retrieveActiveSellers(TimePeriod timePeriod)
            throws ExternalServiceException {
        try {
            logger.info("Retrieving active sellers from TMF API between {} and {}", timePeriod.getStartDateTime(), timePeriod.getEndDateTime());

            Set<String> sellersIds = new TreeSet<>();
            List<Organization> activeSellers = new ArrayList<>();

            List<CustomerBill> bills = this.retrieveBills(null, timePeriod);
            for (CustomerBill cb : bills) {
                if (cb == null) continue;

                if (cb.getRelatedParty() != null) {
                    for (it.eng.dome.tmforum.tmf678.v4.model.RelatedParty rp : cb.getRelatedParty()) {
                        if (RelatedPartyUtils.customerBillHasPartyWithRole(cb, rp.getId(), Role.SELLER)) {
                            sellersIds.add(rp.getId());
                        }
                    }
                }
            }

            for (String s : sellersIds) {
                logger.debug("Retrieving organisation with id: {}", s);
                try {
                    Organization org = this.getOrganization(s);
                    if (org != null) {
                        logger.debug("{} {} {}", org.getTradingName(), org.getName(), org.getId());
                        activeSellers.add(org);
                    }
                } catch (Exception e) {
                    logger.error("unable to retrieve organisation with id {} appearing as seller", s);
                    logger.error("", e);
                    throw e;
                }
            }

            return activeSellers;
        } catch (Exception e) {
            logger.error("Failed to retrieve active sellers", e);
            throw new ExternalServiceException("Failed to retrieve active sellers", e);
        }
    }

    public List<Organization> listReferralsProviders(String referrerOrganizationId)
            throws BadTmfDataException, ExternalServiceException {
        logger.info("List referrals for referrerOrganizationId {}", referrerOrganizationId);

        if (referrerOrganizationId == null || referrerOrganizationId.isEmpty()) {
            throw new BadTmfDataException("ReferrerOrganization", referrerOrganizationId, "Referrer Organization ID cannot be null or empty");
        }

        try {
            List<Organization> referrals = new ArrayList<>();

            Map<String, String> filter = new HashMap<>();
            filter.put("partyCharacteristic.name", "referredBy");

            //FIXME: fix retrieve of large Organization lists
            List<Organization> orgs = FetchUtils.streamAll(
                    apiPartyApis::listOrganizations,   // method reference
                    null,                       	  // fields
                    filter,            				 // filter
                    100                             // pageSize
            ).toList();

            for (Organization o : orgs) {
                if (o.getPartyCharacteristic() != null && o.getPartyCharacteristic().stream()
                        .anyMatch(pc -> "referredBy".equals(pc.getName()) && referrerOrganizationId.equals(pc.getValue()))) {
                    referrals.add(o);
                }
            }
            return referrals;
        } catch (Exception e) {
            logger.error("Error retrieving referrals for referrer provider ID: {}", referrerOrganizationId, e);
            throw new ExternalServiceException("Failed to retrieve referred providers for ID: " + referrerOrganizationId, e);
        }
    }

    public Organization getReferrerProvider(String referralOrganizationId)
            throws BadTmfDataException, ExternalServiceException {
        logger.info("Get referrer for Organization with ID {}", referralOrganizationId);

        if (referralOrganizationId == null || referralOrganizationId.isEmpty()) {
            throw new BadTmfDataException("ReferralOrganization", referralOrganizationId, "Referral Organization ID cannot be null or empty");
        }

        try {
            Organization referralOrg = this.getOrganization(referralOrganizationId);

            if (referralOrg != null && referralOrg.getPartyCharacteristic() != null) {
                String referrerId = null;
                for (Characteristic pc : referralOrg.getPartyCharacteristic()) {
                    if ("referredBy".equals(pc.getName())) {
                        referrerId = (String) pc.getValue();
                        break;
                    }
                }
                if (referrerId != null) {
                    return this.getOrganization(referrerId);
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("Error retrieving referrer provider for referral provider ID: {}", referralOrganizationId, e);
            throw new ExternalServiceException("Failed to retrieve referrer provider for referral ID: " + referralOrganizationId, e);
        }
    }

    public List<Organization> getAllPaginatedOrg() throws ExternalServiceException {
        logger.info("Retrieving all organizations from TMF API");
        try {
            //FIXME: fix retrieve of large Organization lists
            List<Organization> allOrgs = FetchUtils.streamAll(
                    apiPartyApis::listOrganizations,   // method reference
                    null,                       	  // fields
                    null,            				 // filter
                    20                             // pageSize
            ).toList();
            logger.info("Retrieved {} organizations from TMF API", allOrgs.size());
            return allOrgs;
        } catch (Exception e) {
            logger.error("Failed to retrieve all organizations", e);
            throw new ExternalServiceException("Failed to retrieve all organizations", e);
        }
    }

    // ======== TMF BILLING ACCOUNTS ========

    public BillingAccountRef retrieveBillingAccountByProductId(String productId)
            throws BadTmfDataException, ExternalServiceException {
        logger.debug("Retrieving Billing Account from TMF API By Product with id: {}", productId);

        if (productId == null) {
            throw new BadTmfDataException("Product", productId, "Product ID cannot be null");
        }

        try {
            Product prod = this.productApis.getProduct(productId, null);
            BillingAccountRef ba = prod.getBillingAccount();

            if (ba == null) {
                logger.info("No billing accounts found for product with id {}: ", productId);
                return null;
            }

            return ba;
        } catch (Exception e) {
            logger.error("Failed to retrieve billing account for product {}", productId, e);
            throw new ExternalServiceException("Failed to retrieve billing account for product ID: " + productId, e);
        }
    }

    // ======== TMF PRODUCTS ========

    public Product getProductById(String productId, String fields)
            throws BadTmfDataException, ExternalServiceException {
        if (productId == null) {
            throw new BadTmfDataException("Product", productId, "Product ID cannot be null");
        }

        try {
            Product prod = this.productApis.getProduct(productId, fields);
            if (prod == null) {
                logger.info("No product found for product with id {}: ", productId);
                return null;
            }
            return prod;
        } catch (Exception e) {
            logger.error("Failed to retrieve product {}", productId, e);
            throw new ExternalServiceException("Failed to retrieve product with ID: " + productId, e);
        }
    }

    public List<Product> getAllProducts(String fields, Map<String, String> filter)
            throws ExternalServiceException {
        try {
            //FIXME: fix retrieve of large Product lists
            return FetchUtils.streamAll(
                    productApis::listProducts,    // method reference
                    fields,                       		   // fields
                    filter,            					   // filter
                    100                         	   // pageSize
            ).toList();
        } catch (Exception e) {
            logger.error("Failed to retrieve products", e);
            throw new ExternalServiceException("Failed to retrieve products", e);
        }
    }

    public List<ProductOffering> getAllSubscriptionProductOfferings()
            throws ExternalServiceException {
        try {
            CategoryApis categoryApis = new CategoryApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient());
            List<Category> listCategory = categoryApis.getAllCategory(null, null);

            Map<String, String> filter = new HashMap<>();
            for (Category c : listCategory) {
                if (c.getName() != null && c.getName().equalsIgnoreCase("DOME OPERATOR Plan")) {
                    filter.put("category.id", c.getId());
                }
            }

            return this.getAllProductOfferings(null, filter);
        } catch (Exception e) {
            logger.error("Failed to retrieve subscription product offerings", e);
            throw new ExternalServiceException("Failed to retrieve subscription product offerings", e);
        }
    }

    public List<Product> getAllSubscriptionProducts()
            throws ExternalServiceException {
        try {
            List<String> offeringIds = new ArrayList<>();
            List<ProductOffering> pos = this.getAllSubscriptionProductOfferings();
            for (ProductOffering po : pos) {
                offeringIds.add(po.getId());
            }

            Map<String, String> filter = new HashMap<>();
            filter.put("productOffering.id", String.join(",", offeringIds)); // OR

            return this.getAllProducts(null, filter);
        } catch (Exception e) {
            logger.error("Failed to retrieve subscription products", e);
            throw new ExternalServiceException("Failed to retrieve subscription products", e);
        }
    }

    // ======== PRODUCT OFFERING ========

    public ProductOffering getProductOfferingById(String poId, String fields)
            throws BadTmfDataException, ExternalServiceException {
        logger.debug("Retrieving Product Offering from TMF API By Product Offering with id: {}", poId);

        if (poId == null) {
            throw new BadTmfDataException("ProductOffering", poId, "Product Offering ID cannot be null");
        }

        try {
            return this.productCatalogManagementApis.getProductOffering(poId, fields);
        } catch (Exception e) {
            logger.error("Failed to retrieve product offering {}", poId, e);
            throw new ExternalServiceException("Failed to retrieve product offering with ID: " + poId, e);
        }
    }

    public List<ProductOffering> getAllProductOfferings(String fields, Map<String, String> filter)
            throws ExternalServiceException {
        try {
            //FIXME: fix retrieve of large Product Offering lists
            return FetchUtils.streamAll(
                    productCatalogManagementApis::listProductOfferings,    // method reference
                    fields,                       		   // fields
                    filter,            					   // filter
                    100                         	   // pageSize
            ).toList();
        } catch (Exception e) {
            logger.error("Failed to retrieve product offerings", e);
            throw new ExternalServiceException("Failed to retrieve product offerings", e);
        }
    }

    // ======== PRODUCT OFFERING PRICE ========
    public ProductOfferingPrice getProductOfferingPrice(String popId, String fields)
            throws BadTmfDataException, ExternalServiceException {
        if (popId == null) {
            throw new BadTmfDataException("ProductOfferingPrice", popId, "Product Offering Price ID cannot be null");
        }

        try {
            return this.productCatalogManagementApis.getProductOfferingPrice(popId, fields);
        } catch (Exception e) {
            logger.error("Failed to retrieve product offering price {}", popId, e);
            throw new ExternalServiceException("Failed to retrieve product offering price with ID: " + popId, e);
        }
    }

    public Organization getOrganization(String organizationId)
            throws BadTmfDataException, ExternalServiceException {
        if (organizationId == null) {
            throw new BadTmfDataException("Organization", organizationId, "Organization ID cannot be null");
        }

        try {
            return this.apiPartyApis.getOrganization(organizationId, null);
        } catch (Exception e) {
            logger.error("Failed to retrieve organization {}", organizationId, e);
            throw new ExternalServiceException("Failed to retrieve organization with ID: " + organizationId, e);
        }
    }

    public List<Organization> listActiveSellersBehindFederatedMarketplace(String federatedMarketplaceId, TimePeriod timePeriod)
            throws BadTmfDataException, ExternalServiceException {
        if (federatedMarketplaceId == null || federatedMarketplaceId.isEmpty()) {
            throw new BadTmfDataException("FederatedMarketplace", federatedMarketplaceId, "Federated Marketplace ID cannot be null or empty");
        }

        try {
            List<CustomerBill> bills = this.retrieveBills(federatedMarketplaceId, Role.REFERENCE_MARKETPLACE, timePeriod);
            Set<String> sellersIds = new TreeSet<>();

            for (CustomerBill cb : bills) {
                if (cb == null) continue;

                if (cb.getRelatedParty() != null) {
                    for (it.eng.dome.tmforum.tmf678.v4.model.RelatedParty rp : cb.getRelatedParty()) {
                        if (RelatedPartyUtils.customerBillHasPartyWithRole(cb, rp.getId(), Role.SELLER)) {
                            sellersIds.add(rp.getId());
                        }
                    }
                }
            }

            List<Organization> activeSellers = new ArrayList<>();
            for (String s : sellersIds) {
                logger.debug("Retrieving organisation with id: {}", s);
                try {
                    Organization org = this.getOrganization(s);
                    if (org != null) {
                        logger.debug("{} {} {}", org.getTradingName(), org.getName(), org.getId());
                        activeSellers.add(org);
                    }
                } catch (Exception e) {
                    logger.error("unable to retrieve organisation with id {} appearing as seller", s);
                    logger.error("", e);
                    throw e;
                }
            }

            return activeSellers;
        } catch (Exception e) {
            logger.error("Failed to retrieve sellers behind federated marketplace {}", federatedMarketplaceId, e);
            throw new ExternalServiceException("Failed to retrieve sellers behind federated marketplace ID: " + federatedMarketplaceId, e);
        }
    }

}
