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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Characteristic;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf632.v4.model.RelatedParty;
import it.eng.dome.tmforum.tmf678.v4.api.AppliedCustomerBillingRateApi;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;

@Component(value = "tmfDataRetriever")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TmfDataRetriever implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(TmfDataRetriever.class);

    @Autowired
    // Factory for TMF APIss
    private TmfApiFactory tmfApiFactory;

    // TMForum API to retrieve bills
    private AppliedCustomerBillingRateApi billApi;
    private OrganizationApi orgApi;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.billApi = new AppliedCustomerBillingRateApi(tmfApiFactory.getTMF678CustomerBillApiClient());
        this.orgApi = new OrganizationApi(tmfApiFactory.getTMF632PartyManagementApiClient());
        logger.info("TmfDataRetriever initialized with billApi and orgApi");
    }

    public TmfDataRetriever() {
    }

    // retrieve all bills in the specified period, optionally filtered by sellerId
    // if sellerId is null, all bills in the period are retrieved
    public List<AppliedCustomerBillingRate> retrieveBills(String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {

        logger.debug("Retrieving bills from TMF API between " + from + " and " + to);

        // prepare the filter: only billed bills and in the given period
        Map<String, String> filter = new HashMap<>();
        filter.put("isBilled", "true");
        if(from!=null)
            filter.put("date.gt", from.toString());
        if(to!=null)
            filter.put("date.lt", to.toString());
        if(sellerId!=null) {
            filter.put("relatedParty", sellerId);
            filter.put("relatedParty.role", "Seller");
            logger.debug("Retrieving bills for seller with id: " + sellerId);
        } else {
            logger.debug("Retrieving all bills in the specified period");
        }

        // retrieve bills
        List<AppliedCustomerBillingRate> out = billApi.listAppliedCustomerBillingRate(null, null, 1000, filter);

        logger.debug("found " + out.size() + " bills in the specified period");
        return out;
    }

    // retrieve all providers with at least one bill in the specified period
    public List<Organization> retrieveActiveSellers(OffsetDateTime from, OffsetDateTime to) throws Exception {

        logger.debug("Retrieving active sellers from TMF API between " + from + " and " + to);

        // id of sellers from bills
        Set<String> sellersIds = new TreeSet<>(); 

        // outuput, the organisations retrieved from the API
        List<Organization> activeSellers = new ArrayList<>();

        // prepare the filter: only billed bills and in the given period
        Map<String, String> filter = new HashMap<>();
        filter.put("isBilled", "true");
        if(from!=null)
            filter.put("date.gt", from.toString());
        if(to!=null)
            filter.put("date.lt", to.toString());

        // retrieve bills and extract seller ids
        List<AppliedCustomerBillingRate> bills = this.retrieveBills(null, from, to);
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
        return this.retrieveActiveSellers(from, to);
    }

    public List<AppliedCustomerBillingRate> retrieveBillsForSellerInLastMonth(String sellerId) throws Exception {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.plusMonths(-1);
        return this.retrieveBills(sellerId, from, to);
    }

    public List<AppliedCustomerBillingRate> retrieveBillsInLastMonth() throws Exception {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.plusMonths(-1);
        return this.retrieveBills(null, from, to);
    }


}

