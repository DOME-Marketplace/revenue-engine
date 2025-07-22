package it.eng.dome.revenue.engine.controller;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.service.PlanService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;

@RestController
//@RequiredArgsConstructor
@RequestMapping("/dev2/revenue")
public class StatementsController {
    
	protected final Logger logger = LoggerFactory.getLogger(StatementsController.class);

	@Autowired
	PriceCalculator priceCalculator;

	@Autowired
	SubscriptionService subscriptionService;

	@Autowired
	PlanService subscriptionPlanService;
	
	@Autowired
    TmfDataRetriever tmfDataRetriever;

    public StatementsController() {
    }
    
    @GetMapping("/subscriptions/{id}/statements")
    public ResponseEntity<RevenueStatement> statementCalculator(@PathVariable String id) {    	
        try {
            Subscription sub = subscriptionService.getBySubscriptionId(id);
            logger.info("Subscription: {}", sub);
            
            OffsetDateTime time = OffsetDateTime.now();            
            
            priceCalculator.setSubscription(sub);
                        
            Plan plan = subscriptionPlanService.findPlanById(sub.getPlan().getId());
            logger.info("Plan: {}", plan);
            
            Price price = plan.getPrice();
            
            RevenueItem computedRevenueItem = priceCalculator.compute(price, time);
            RevenueStatement computedRevenueStatement = new RevenueStatement(sub, new SubscriptionTimeHelper(sub).getSubscriptionPeriodAt(time));
            computedRevenueStatement.setRevenueItem(computedRevenueItem);
            computedRevenueStatement.setSubscription(sub);
            
            return ResponseEntity.ok(computedRevenueStatement);
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
