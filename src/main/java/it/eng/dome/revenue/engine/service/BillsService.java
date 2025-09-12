package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.ProductApis;
import it.eng.dome.revenue.engine.invoicing.InvoicingService;
import it.eng.dome.revenue.engine.mapper.RevenueBillingMapper;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.comparator.SimpleBillComparator;
import it.eng.dome.revenue.engine.service.cached.CachedStatementsService;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillRef;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class BillsService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(BillsService.class);

    @Autowired
    private TmfApiFactory tmfApiFactory;

    @Autowired
	private CachedStatementsService statementsService;

    @Autowired
	private SubscriptionService subscriptionService;
    
    @Autowired
	private TmfDataRetriever tmfDataRetriever;

    @Autowired
	private InvoicingService invoicingService;

    private ProductApis productApis;

    @Override
    public void afterPropertiesSet() throws Exception {
        productApis = new ProductApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
    }

    /**
	 * Retrieves a bill by its ID.
	 * 
	 * @param billId the ID of the bill to retrieve
	 * @return the SimpleBill object if found, null otherwise
	 * @throws Exception if an error occurs during retrieval
	*/
    public SimpleBill getBill(String billId) throws Exception {
    	logger.info("Fetch bill with ID {}", billId);
        // FIXME: temporary... until we have proper persistence
        // extract the subscription id
        String subscriptionId = "urn:ngsi-ld:product:"+billId.substring(23, 23+36);

        // iterate over bills for that subscription, until found
        for(SimpleBill bill: this.getSubscriptionBills(subscriptionId)) {
            if(billId.equals(bill.getId()))
                return bill;
        }
        return null;
    }
    
    /** Retrieves all bills for a given subscription ID.
	 * 
	 * @param subscriptionId the ID of the subscription for which to retrieve bills
	 * @return a list of SimpleBill objects representing the bills for the subscription
	 * @throws Exception if an error occurs during retrieval
	*/
    public List<SimpleBill> getSubscriptionBills(String subscriptionId) throws Exception {    
	    logger.info("Fetch bills for subscription with ID{}", subscriptionId);
        try {
            Set<SimpleBill> bills = new TreeSet<>(new SimpleBillComparator());
            Subscription subscription = this.subscriptionService.getSubscriptionByProductId(subscriptionId);
            List<RevenueItem> items = this.statementsService.getItemsForSubscription(subscriptionId);
            for(TimePeriod tp: this.statementsService.getBillPeriods(subscriptionId)) {
                SimpleBill bill = new SimpleBill();
                bill.setRelatedParties(subscription.getRelatedParties());
                bill.setSubscriptionId(subscription.getId());
                bill.setPeriod(tp);
                for(RevenueItem item:items) {
                    bill.addRevenueItem(item);
                }
                bills.add(bill);
            }
            return new ArrayList<>(bills);
        } catch (Exception e) {
        	logger.error("Error retrieving subscription bills for ID {}: {}", subscriptionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
	 * Builds a CustomerBill from a SimpleBill.
	 * 
	 * @param sb the SimpleBill to convert
	 * @return a CustomerBill object
	 * @throws IllegalArgumentException if the SimpleBill is null or does not contain related party information
	 */
    
    public CustomerBill buildCB(SimpleBill sb) {
        if (sb == null || sb.getRelatedParties() == null || sb.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in SimpleBill");
        }
        
        // Retrieve the related party with role = "Buyer"
        RelatedParty buyerParty = this.getBuyerParty(sb.getRelatedParties());
        
        BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(buyerParty.getId());
        return RevenueBillingMapper.toCB(sb, billingAccountRef);
    }
    
    public List<AppliedCustomerBillingRate> getABCRList(SimpleBill sb) {
        if (sb == null || sb.getRelatedParties() == null || sb.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in SimpleBill");
        }
        
        Subscription subscription = subscriptionService.getSubscriptionByProductId(sb.getSubscriptionId());
        
        // Retrieve the related party with role = "Buyer"
        RelatedParty buyerParty = this.getBuyerParty(sb.getRelatedParties());
        
        BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(buyerParty.getId());
        
        List<AppliedCustomerBillingRate> acbrList = RevenueBillingMapper.toACBRList(sb, subscription, billingAccountRef);
        
        acbrList = this.applyCustomerBillRef(acbrList);
        
        acbrList = this.applyTaxes(acbrList);
        
        return acbrList;
    }
    
    private RelatedParty getBuyerParty(List<RelatedParty> relatedParties) {
    	if (relatedParties == null || relatedParties.isEmpty()) {
    		throw new IllegalArgumentException("Missing related party information in SimpleBill");
    	}
    	
    	return relatedParties.stream()
    			.filter(rp -> "Buyer".equalsIgnoreCase(rp.getRole()))
    			.findFirst()
    			.orElseThrow(() -> new IllegalArgumentException("No related party with role 'Buyer' found"));
    }

    public List<AppliedCustomerBillingRate> applyTaxes(List<AppliedCustomerBillingRate> acbrs) {

        // first, retrieve the product
        String productId = acbrs.get(0).getProduct().getId();
        Product product = productApis.getProduct(productId, null);

        // invoke the invoicing servi
        return this.invoicingService.applyTaxees(product, acbrs);
    }
    
    public List<AppliedCustomerBillingRate> applyCustomerBillRef(List<AppliedCustomerBillingRate> acbrs){
		// FIXME: Currently, we don't consider persistence.
		BillRef billRef = new BillRef();
//		String billId = sb.getId();
//		billRef.setId(billId.replace("urn:ngsi-ld:simplebill", "urn:ngsi-ld:customerbill"));
		billRef.setId("urn:ngsi-ld:customerbill:" + UUID.randomUUID().toString());
		
		for (AppliedCustomerBillingRate acbr : acbrs) {
			acbr.setBill(billRef);
		}
		
		return acbrs;
    }

}
