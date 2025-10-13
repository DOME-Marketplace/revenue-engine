package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class DevDashboardService {

    private final Logger logger = LoggerFactory.getLogger(BillsService.class);

    @Autowired
	private TmfCachedDataRetriever tmfDataRetriever;

    public List<Organization> listOrganizations() throws Exception {
        return tmfDataRetriever.getAllPaginatedOrg();
    }

    public List<CustomerBill> listOrganizationTransactions(String sellerId) throws Exception {
        // considering only the last year
        TimePeriod tp = new TimePeriod();
        tp.setEndDateTime(OffsetDateTime.now());
        tp.setStartDateTime(OffsetDateTime.now().minusYears(1));
        return tmfDataRetriever.retrieveBills(sellerId, tp);
    }

    public CustomerBill getCustomerBill(String customerbillId) throws Exception {
        return tmfDataRetriever.getCustomerBillById(customerbillId);
    }

    public List<AppliedCustomerBillingRate> getAppliedCustomerBillingRates(String customerbillId) throws Exception {
        return tmfDataRetriever.getACBRsByCustomerBillId(customerbillId);
    }

}
