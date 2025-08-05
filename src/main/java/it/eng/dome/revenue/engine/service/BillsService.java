package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.mapper.RevenueBillingMapper;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.comparator.SimpleBillComparator;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class BillsService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(BillsService.class);

    @Autowired
	private StatementsService statementsService;

    @Autowired
	private SubscriptionService subscriptionService;
    
    @Autowired
	private TmfDataRetriever tmfDataRetriever;

    @Override
    public void afterPropertiesSet() throws Exception {
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
        String subscriptionId = "urn:ngsi-ld:subscription:"+billId.substring(23, 23+36+1+36);

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
            Subscription subscription = this.subscriptionService.getSubscriptionById(subscriptionId);
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
    
    public List<AppliedCustomerBillingRate> buildABCRList(SimpleBill sb) {
        if (sb == null || sb.getRelatedParties() == null || sb.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in SimpleBill");
        }
        
        Subscription subscription = null;
    	try {
    		subscription = subscriptionService.getSubscriptionById(sb.getSubscriptionId());
    	} catch (ApiException | IOException e) {
    		logger.error("Error retrieving subscription ID {}: {}", sb.getSubscriptionId(), e.getMessage(), e);
    		throw new IllegalStateException("Failed to retrieve subscription data", e);
    	}
        
        // Retrieve the related party with role = "Buyer"
        RelatedParty buyerParty = this.getBuyerParty(sb.getRelatedParties());
        
        BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(buyerParty.getId());
        return RevenueBillingMapper.toACBRList(sb, subscription, billingAccountRef);
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
}
