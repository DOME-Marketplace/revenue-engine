package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Calendar;
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
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;
import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
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
        List<AppliedCustomerBillingRate> out = billApi.listAppliedCustomerBillingRate(null, null, 1000, filter);
        for(AppliedCustomerBillingRate acbr: out) {
            if(acbr==null || acbr.getRelatedParty()==null)
                continue;
            for(RelatedParty rp: acbr.getRelatedParty()) {
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
                e.printStackTrace();
            }
        }

        return activeSellers;

    }

    public List<Organization> retrieveActiveSellersInLastMonth() throws Exception {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.plusMonths(-1);
        return this.retrieveActiveSellers(from, to);
    }


}

