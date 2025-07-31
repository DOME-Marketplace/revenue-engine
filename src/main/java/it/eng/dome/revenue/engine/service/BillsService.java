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

import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.comparator.SimpleBillComparator;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class BillsService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(BillsService.class);

    @Autowired
	private StatementsService statementsService;

    @Autowired
	private SubscriptionService subscriptionService;

    @Override
    public void afterPropertiesSet() throws Exception {
    }


    public List<SimpleBill> getSubscriptionBills(String subscriptionId) throws Exception {    	   
        try {
            Set<SimpleBill> bills = new TreeSet<>(new SimpleBillComparator());
            Subscription subscription = this.subscriptionService.getSubscriptionById(subscriptionId);
            List<RevenueItem> items = this.statementsService.getItemsForSubscription(subscriptionId);
            for(TimePeriod tp: this.statementsService.getBillPeriods(subscriptionId)) {
                SimpleBill bill = new SimpleBill();
                bill.setRelatedParties(subscription.getRelatedParties());
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
    
//    public List<SimpleBill> getFilteredBills(String subscriptionId, TimePeriod timePeriodFilter, Boolean estimated) {
//        List<SimpleBill> allBills;
//
//        try {
//            // Retrieve all bills associated with the given subscription
//            allBills = this.getSubscriptionBills(subscriptionId);
//        } catch (Exception e) {
//            // Log the error and return an empty list if something goes wrong
//            logger.error("Failed to retrieve bills for subscriptionId: {}", subscriptionId, e);
//            return Collections.emptyList(); // fallback: empty list
//        }
//
//        List<SimpleBill> filteredBills = new ArrayList<>();
//        for (SimpleBill bill : allBills) {
//            TimePeriod billPeriod = bill.getPeriod();
//            logger.info("Bill with period: {}", billPeriod);
//
//            // Skip malformed bills that are missing period data
//            if (billPeriod == null || billPeriod.getEndDateTime() == null) {
//                logger.warn("Skipping bill with missing or invalid period: {}", bill);
//                continue;
//            }
//
//            OffsetDateTime billEnd = billPeriod.getEndDateTime();
//
//            // Check if the bill falls within the given time period filter
//            boolean timeMatch = true;
//            if (timePeriodFilter != null) {
//                OffsetDateTime start = timePeriodFilter.getStartDateTime();
//                OffsetDateTime end = timePeriodFilter.getEndDateTime();
//
//                // Match bills that end within the range [start, end]
//                timeMatch = (start == null || !billEnd.isBefore(start)) &&
//                            (end == null || !billEnd.isAfter(end));
//            }
//
//            // Check if the bill matches the estimated filter
//            boolean estimateMatch = (estimated == null) || estimated.equals(bill.isEstimated());
//
//            if (timeMatch && estimateMatch) {
//                filteredBills.add(bill);
//            }
//        }
//
//        return filteredBills;
//    }
    
}
       
