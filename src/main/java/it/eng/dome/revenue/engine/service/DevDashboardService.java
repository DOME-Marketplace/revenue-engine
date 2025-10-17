package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.comparator.CustomerBillComparator;
import it.eng.dome.revenue.engine.model.comparator.OrganizationComparator;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.utils.RelatedPartyUtils;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class DevDashboardService {

//    private final Logger logger = LoggerFactory.getLogger(BillsService.class);

    @Autowired
	private TmfCachedDataRetriever tmfDataRetriever;

    public List<Organization> listOrganizations() throws Exception {
        List<Organization> orgs = tmfDataRetriever.getAllPaginatedOrg();
        Collections.sort(orgs, new OrganizationComparator());
        return orgs;
    }

    public List<CustomerBill> listOrganizationTransactions(String sellerId) throws Exception {

        // considering only the last year
        TimePeriod tp = new TimePeriod();
        tp.setEndDateTime(OffsetDateTime.now());
        tp.setStartDateTime(OffsetDateTime.now().minusYears(1));

        // sort transactions by billDate
        List<CustomerBill> bills = tmfDataRetriever.retrieveBills(sellerId, tp);
        bills.sort(new CustomerBillComparator());

        return bills;
    }

    public CustomerBill getCustomerBill(String customerbillId) throws Exception {
        return tmfDataRetriever.getCustomerBillById(customerbillId);
    }

    public List<AppliedCustomerBillingRate> getAppliedCustomerBillingRates(String customerbillId) throws Exception {
        return tmfDataRetriever.getACBRsByCustomerBillId(customerbillId);
    }

    public List<Product> getPurchasedproducts(String buyerId) throws Exception {
        Map<String, String> filter = new HashMap<>();
        filter.put("relatedParty.id", buyerId);
        List<Product> products = tmfDataRetriever.getAllProducts(null, filter);
        return RelatedPartyUtils.retainProductsWithParty(products, buyerId, Role.BUYER);
    }

    public List<Product> getSoldProducts(String sellerId) throws Exception {
        Map<String, String> filter = new HashMap<>();
        filter.put("relatedParty.id", sellerId);
        List<Product> products = tmfDataRetriever.getAllProducts(null, filter);
        return RelatedPartyUtils.retainProductsWithParty(products, sellerId, Role.SELLER);
    }




}
