package it.eng.dome.revenue.engine.service;

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


    public SimpleBill getBill(String billId) throws Exception {
        // FIXME: temporary... until we have proper persistence
        // extract the subscription id
        String subscriptionId = "urn:ngsi-ld:subscription:"+billId.substring(23, 23+36+1+36);
        logger.debug("Subscription id is: " + subscriptionId);

        // iterate over bills for that subscription, until found
        for(SimpleBill bill: this.getSubscriptionBills(subscriptionId)) {
            if(billId.equals(bill.getId()))
                return bill;
        }
        return null;
    }

    public List<SimpleBill> getSubscriptionBills(String subscriptionId) throws Exception {    	   
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
           logger.error(e.getMessage(), e);
           throw(e);
        }
    }
    
    public CustomerBill buildCB(SimpleBill sb) {
        if (sb == null || sb.getRelatedParties() == null || sb.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in SimpleBill");
        }
        
        // Retrieve the related party with role = "Buyer"
        RelatedParty buyerParty = sb.getRelatedParties().stream()
            .filter(rp -> "Buyer".equalsIgnoreCase(rp.getRole()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No related party with role 'Buyer' found"));
        
        BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(buyerParty.getId());
        return RevenueBillingMapper.toCB(sb, billingAccountRef);
    }
}
