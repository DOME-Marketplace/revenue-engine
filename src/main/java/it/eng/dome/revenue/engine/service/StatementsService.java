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

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.model.comparator.RevenueItemComparator;
import it.eng.dome.revenue.engine.model.comparator.RevenueStatementTimeComparator;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class StatementsService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(StatementsService.class);

	@Autowired
	private SubscriptionService subscriptionService;

    @Autowired
	private PlanService planService;

    @Autowired
	private PriceCalculator priceCalculator;

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    /*
     * Returns a sorted list of RevenueItems
     */
    public List<RevenueItem> getItemsForSubscription(String subscriptionId) throws Exception {    	
        List<RevenueStatement> statements = this.getStatementsForSubscription(subscriptionId);
        Set<RevenueItem> items = new TreeSet<>(new RevenueItemComparator());
        for(RevenueStatement s:statements) {
            for(RevenueItem i:s.getRevenueItems()) {
                items.add(i);
            }
        }
        return new ArrayList<>(items);
    }

    /**
	 * Returns a set of TimePeriods for the billing periods of the subscription
	 * 
	 * @param subscriptionId The ID of the subscription for which to retrieve billing periods.
	 * @return A set of TimePeriod objects representing the billing periods.
	 * @throws Exception If an error occurs during retrieval.
	 */
    public Set<TimePeriod> getBillPeriods(String subscriptionId) throws Exception {

            // retrieve the subscription by id
            Subscription sub = subscriptionService.getSubscriptionById(subscriptionId);

            // retrive the plan for the subscription
            Plan plan = this.planService.findPlanById(sub.getPlan().getId());

            // add the full plan to the subscription
            sub.setPlan(plan);

            // configure the price calculator
            priceCalculator.setSubscription(sub);

            // build all statements
            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(sub);

            return timeHelper.getBillingTimePeriods();
        }

    /*
     * Returns a sorted list of RevenueStatements
     */
    public List<RevenueStatement> getStatementsForSubscription(String subscriptionId) throws Exception {    
		logger.info("Call to getStatementsForSubscription: {}", subscriptionId);
        try {

            // prepare output
            Set<RevenueStatement> statements = new TreeSet<>(new RevenueStatementTimeComparator());

            // retrieve the subscription by id
            Subscription sub = subscriptionService.getSubscriptionById(subscriptionId);

            // retrive the plan for the subscription
            Plan plan = this.planService.findPlanById(sub.getPlan().getId());

            // add the full plan to the subscription
            sub.setPlan(plan);

            // configure the price calculator
            priceCalculator.setSubscription(sub);

            // build all statements
            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(sub);
            for(TimePeriod tp : timeHelper.getChargePeriodTimes()) {
                RevenueStatement statement = priceCalculator.compute(tp);
                if(statement!=null) {
                    statement.clusterizeItems();
                    statements.add(statement);
                }
            }

            // replace the plan with a reference
            sub.setPlan(plan.buildRef());

            return new ArrayList<>(statements);
        } catch (Exception e) {
            logger.error("Failed to generate statements for subscription {}: {}", subscriptionId, e.getMessage(), e);
            throw e;
        }
    }    


}
