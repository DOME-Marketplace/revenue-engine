package it.eng.dome.revenue.engine.service;

import java.net.URI;
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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.brokerage.api.AccountManagementApis;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Characteristic;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf666.v4.model.BillingAccount;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Component(value = "tmfDataRetriever")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TmfDataRetriever implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(TmfDataRetriever.class);

    @Autowired
    // Factory for TMF APIss
    private TmfApiFactory tmfApiFactory;

    // TMForum API to retrieve bills
    private AppliedCustomerBillRateApis billApi;
    private OrganizationApi orgApi;
    private AccountManagementApis accountApi;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.billApi = new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
        this.orgApi = new OrganizationApi(tmfApiFactory.getTMF632PartyManagementApiClient());
        this.accountApi = new AccountManagementApis(tmfApiFactory.getTMF666AccountManagementApiClient());
        logger.info("TmfDataRetriever initialized with billApi and orgApi");
    }

    public TmfDataRetriever() {
    }
    
	// Retrieve all bills in the specified period, optionally filtered by relatedPartyId and billing status
	// If relatedPartyId is null, all bills in the period are retrieved
	// If isBilled is null, the billed filter is not applied
	// If isSeller is true, filters by relatedParty.role = "Seller"
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

        if (sellerId != null) {
            filter.put("relatedParty", sellerId);
            filter.put("relatedParty.role", "Seller");
            logger.debug("Retrieving bills for seller with id: " + sellerId);
        } else {
            logger.debug("Retrieving all bills in the specified period");
        }

        List<AppliedCustomerBillingRate> out = billApi.getAllAppliedCustomerBillingRates(null, filter);
        logger.debug("Found " + out.size() + " bills in the specified period");
        return out;
    }

    // retrieve all providers with at least one bill in the specified period
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

    public List<Organization> listReferralsProviders(String referrerOrganizationId) throws Exception {
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

    public Organization getReferrerProvider(String referralOrganizationId) throws Exception{
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
    
    public BillingAccountRef retrieveBillingAccountByRelatedPartyId(String relatedPartyId) {
        logger.debug("Retrieving Billing Account from TMF API By RelatedParty with id: " + relatedPartyId);
        
        if (relatedPartyId == null) {
                logger.warn("RelatedParty ID is null, cannot retrieve billing account.");
                return null;
        }
        
        try {
            // filter
            Map<String, String> filter = new HashMap<>();
//            filter.put("relatedParty.id", URLEncoder.encode(relatedPartyId, StandardCharsets.UTF_8));
            filter.put("relatedParty.id", relatedPartyId);

//            List<BillingAccount> billAccs = accountApi.getAllBillingAccounts(null, null, 1000, filter);
            List<BillingAccount> billAccs = accountApi.getAllBillingAccounts(null, filter);
            
            if (billAccs == null || billAccs.isEmpty()) {
                logger.info("No billing accounts found for related party: " + relatedPartyId);
                return null;
            }

            BillingAccount first = billAccs.get(0);

            BillingAccountRef ref = new BillingAccountRef();
            ref.setId(first.getId());
            URI hrefURI = first.getHref();
            if (hrefURI != null) {
                ref.setHref(hrefURI.toString());
            }
            ref.setName(first.getName());
            ref.setAtReferredType("BillingAccount");

            return ref;

        } catch (Exception e) {
            logger.error("Error retrieving billing account for related party: " + relatedPartyId, e);
            return null;
        }
    }

}

