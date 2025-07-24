package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.List;

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

    public List<RevenueItem> getItemsForSubscription(String subscriptionId) throws Exception {    	
        List<RevenueStatement> statements = this.getStatementsForSubscription(subscriptionId);
        List<RevenueItem> items = new ArrayList<>();
        for(RevenueStatement s:statements) {
            for(RevenueItem i:s.getRevenueItems()) {
                items.add(i);
            }
        }
        return items;
    }

    public List<RevenueStatement> getStatementsForSubscription(String subscriptionId) throws Exception {    	
        try {

            // prepare output
            List<RevenueStatement> statements = new ArrayList<>();

            // retrieve the subscription by id
            Subscription sub = subscriptionService.getSubscriptionById(subscriptionId);
            logger.info("Subscription: {}", sub);

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

            return statements;
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
           throw(e);
        }
    }    


}
