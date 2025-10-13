package it.eng.dome.revenue.engine.service;

import it.eng.dome.revenue.engine.model.*;
import it.eng.dome.revenue.engine.model.comparator.RevenueItemComparator;
import it.eng.dome.revenue.engine.model.comparator.RevenueStatementTimeComparator;
import it.eng.dome.revenue.engine.service.cached.CachedPlanService;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
import it.eng.dome.revenue.engine.service.compute2.RevenueStatementBuilder;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class StatementsService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(StatementsService.class);

	@Autowired
	private CachedSubscriptionService subscriptionService;
    @Autowired
	private CachedPlanService planService;

//    @Autowired
//	private PriceCalculator priceCalculator;

    public void afterPropertiesSet() throws Exception {}

    public StatementsService() {}

	/*
	 * Returns a list of RevenueItems for the subscription
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
	 */
    public Set<TimePeriod> getBillPeriods(String subscriptionId) {

            // retrieve the subscription by id
            Subscription sub = subscriptionService.getSubscriptionByProductId(subscriptionId);

            // retrieve the resolved plan for the subscription
            Plan plan = planService.getResolvedPlanById(sub.getPlan().getId(), sub);
	        sub.setPlan(plan);

            // build all statements
            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(sub);

            return timeHelper.getBillingTimePeriods();
        }

	/**
	 * Retrieves all revenue statements for a given subscription ID.
	 * 
	 * @param subscriptionId The ID of the subscription for which to retrieve statements.
	 * @return A list of RevenueStatement objects representing the statements for the subscription.
	 * @throws Exception If an error occurs during retrieval or computation of statements.
	 */
    public List<RevenueStatement> getStatementsForSubscription(String subscriptionId) throws Exception {
        logger.info("Call to getStatementsForSubscription: {}", subscriptionId);

        Set<RevenueStatement> statements = new TreeSet<>(new RevenueStatementTimeComparator());

        Subscription sub;
        try {
            sub = subscriptionService.getSubscriptionByProductId(subscriptionId);
        } catch (Exception ex) {
            logger.error("Failed to retrieve subscription with ID {}: {}", subscriptionId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to retrieve subscription: " + subscriptionId, ex);
        }

        Plan plan;
        try {
            plan = planService.getResolvedPlanById(sub.getPlan().getId(), sub);
        } catch (Exception ex) {
            logger.error("Failed to retrieve plan for subscription {}: {}", subscriptionId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to retrieve plan for subscription: " + subscriptionId, ex);
        }

        sub.setPlan(plan);

        try {
            RevenueStatementBuilder rsb = new RevenueStatementBuilder(sub);
            for (TimePeriod chargePeriod : sub.getChargePeriods()) {
                logger.debug("\n***************************** BILLING CYCLE ***************************\n {} \n************************************************************************", chargePeriod);
                try {
                    RevenueStatement statement = rsb.buildStatement(chargePeriod);
                    if (statement != null) {
                        statement.clusterizeItems();
                        statements.add(statement);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to compute statement for period {} in subscription {}: {}", chargePeriod, subscriptionId, ex.getMessage(), ex);
                    // Continue processing other periods
                }
            }

        } catch (Exception ex) {
            logger.error("Unexpected error while computing statements for subscription {}: {}", subscriptionId, ex.getMessage(), ex);
            throw new RuntimeException("Error while computing statements for subscription: " + subscriptionId, ex);
        } finally {
            // Replace full plan with reference even in case of exception
            sub.setPlan(plan.buildRef());
        }

        return new ArrayList<>(statements);
    }
    
}
