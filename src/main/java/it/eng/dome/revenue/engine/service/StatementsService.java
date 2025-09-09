package it.eng.dome.revenue.engine.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.ehcache.Cache;
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
    

    private Cache<String, List<RevenueStatement>> statementsCache;

    
    public StatementsService(CacheService cacheService) {
        statementsCache = cacheService.getOrCreateCache(
                "statementsCache",
                String.class,
                (Class<List<RevenueStatement>>)(Class<?>)List.class,
                Duration.ofMinutes(30)
            );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

	/**
	 * Returns a list of RevenueItems for the subscription
	 * @param subscriptionId
	 * @return
	 * @throws Exception
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
            Subscription sub = subscriptionService.getSubscriptionByProductId(subscriptionId);

            // retrive the plan for the subscription
            Plan plan = this.planService.findPlanByOfferingId(sub.getPlan().getId());

            // add the full plan to the subscription
            sub.setPlan(plan);

            // configure the price calculator
            priceCalculator.setSubscription(sub);

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

        // --- TRY CACHE FIRST ---
        List<RevenueStatement> cachedStatements = statementsCache.get(subscriptionId);
        if (cachedStatements != null) {
            logger.info("Returning cached statements for subscription {}", subscriptionId);
            return cachedStatements;
        }

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
            plan = planService.findPlanByOfferingId(sub.getPlan().getId());
        } catch (Exception ex) {
            logger.error("Failed to retrieve plan for subscription {}: {}", subscriptionId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to retrieve plan for subscription: " + subscriptionId, ex);
        }

        sub.setPlan(plan);

        try {
            priceCalculator.setSubscription(sub);

            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(sub);
            for (TimePeriod tp : timeHelper.getChargePeriodTimes()) {
                try {
                    RevenueStatement statement = priceCalculator.compute(tp);
                    if (statement != null) {
                        statement.clusterizeItems();
                        statements.add(statement);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to compute statement for period {} in subscription {}: {}", tp, subscriptionId, ex.getMessage(), ex);
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

        List<RevenueStatement> result = new ArrayList<>(statements);

        // --- STORE IN CACHE ---
        statementsCache.put(subscriptionId, result);

        return result;
    }
    
}
