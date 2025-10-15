package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.RelatedParty;
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

    public List<Product> getPurchasedproducts(String buyerId) throws Exception {
        Map<String, String> filter = new HashMap<>();
        filter.put("relatedParty.id", buyerId);
        List<Product> products = tmfDataRetriever.getAllProducts(null, filter);
        return this.filterByIdAndRole(products, buyerId, "Buyer");
    }

    public List<Product> getSoldProducts(String sellerId) throws Exception {
        Map<String, String> filter = new HashMap<>();
        filter.put("relatedParty.id", sellerId);
        List<Product> products = tmfDataRetriever.getAllProducts(null, filter);
        return this.filterByIdAndRole(products, sellerId, "Seller");
    }

    private List<Product> filterByIdAndRole(List<Product> products, String partyId, String partyRole) {
        List<Product> out = new ArrayList<>();
        for(Product p:products) {
            for(RelatedParty rp: p.getRelatedParty()) {
                if(partyId!=null && partyId.equalsIgnoreCase(rp.getId()) && partyRole!=null && partyRole.equalsIgnoreCase(rp.getRole())) {
                    out.add((p));
                    break;
                }
            }
        }
        return out;
    }

}
