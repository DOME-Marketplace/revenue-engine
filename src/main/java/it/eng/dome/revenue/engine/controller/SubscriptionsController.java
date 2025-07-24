package it.eng.dome.revenue.engine.controller;

import java.util.ArrayList;
import java.util.List;

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
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.service.PlanService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;


@RestController
//@RequiredArgsConstructor
@RequestMapping("revenue/subscriptions")
public class SubscriptionsController {
    
	protected final Logger logger = LoggerFactory.getLogger(StatementsController.class);

	@Autowired
	private PriceCalculator priceCalculator;

	@Autowired
	private SubscriptionService subscriptionService;

	@Autowired
	private PlanService subscriptionPlanService;
	
	@Autowired
	TmfDataRetriever tmfDataRetriever;

    public SubscriptionsController() {
    }

    @GetMapping("")
    public ResponseEntity<List<Subscription>> getAllSubscriptions() {
        try {
//            List<Subscription> subscriptions = subscriptionService.loadAllFromStorage();
            List<Subscription> subscriptions = subscriptionService.getAllSubscriptions();
            return ResponseEntity.ok(subscriptions);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<Subscription> getSubscription(@PathVariable String subscriptionId) {
        try {
//            Subscription subscription = subscriptionService.getBySubscriptionId(subscriptionId);
            Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
            if (subscription == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            else {
                return ResponseEntity.ok(subscription);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("{subscriptionId}/statements")
    public ResponseEntity<List<RevenueStatement>> statementCalculator(@PathVariable String subscriptionId) {    	
        try {

            // prepare output
            List<RevenueStatement> statements = new ArrayList<>();

            // retrieve the subscription by id
            Subscription sub = subscriptionService.getSubscriptionById(subscriptionId);
            logger.info("Subscription: {}", sub);

            // retrive the plan for the subscription
            Plan plan = this.subscriptionPlanService.findPlanById(sub.getPlan().getId());

            // add the full plan to the subscription
            sub.setPlan(plan);

            // configure the price calculator
            priceCalculator.setSubscription(sub);

            // build all statements
            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(sub);
            for(TimePeriod tp : timeHelper.getChargePeriodTimes()) {
                RevenueStatement statement = priceCalculator.compute(tp);
                if(statement!=null) {
                    statements.add(statement);
                }
            }

            // replace the plan with a reference
            sub.setPlan(plan.buildRef());

            return ResponseEntity.ok(statements);
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
//    @GetMapping("/{subscriptionId}/statements")
//    public ResponseEntity<List<RevenueStatement>> sellerStatements(@PathVariable String subscriptionId) {
//        try {
//            List<RevenueStatement> statements = new ArrayList<>();
//
//            // retrieve the subscription by id
//            Subscription subscription = subscriptionService.getBySubscriptionId(subscriptionId);
//
//            // retrive the plan for the subscription
//            Plan plan = this.subscriptionPlanService.findPlanById(subscription.getPlan().getId());
//
//            // add the plan to the subscription
//            subscription.setPlan(plan);
//
//            // build empty, fake statements
//            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(subscription);
//            for(TimePeriod tp : timeHelper.getChargePeriodTimes()) {
//                statements.add(new RevenueStatement(subscription, tp));
//            }
//
//            return ResponseEntity.ok(statements);
//        } catch (Exception e) {
//            logger.error(e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }


//    @GetMapping("/summary")
//    public ResponseEntity<RevenueSummary> getSubscriptionSummaryByProviderId(@PathVariable String id) {    	
//        try {
//            Subscription sub = subscriptionService.getBySubscriptionId(id);
//            logger.info("Subscription: {}", sub);
//            
//            OffsetDateTime time = OffsetDateTime.now();            
//            
//            priceCalculator.setSubscription(sub);
//                        
//            Plan plan = subscriptionPlanService.findPlanById(sub.getPlan().getId());
//            logger.info("Plan: {}", plan);
//            
//            Price price = plan.getPrice();
//            
//            RevenueItem computedRevenueItem = priceCalculator.compute(price, time);
//            RevenueStatement computedRevenueStatement = new RevenueStatement(sub, new SubscriptionTimeHelper(sub).getSubscriptionPeriodAt(time));
//            computedRevenueStatement.setRevenueItem(computedRevenueItem);
//            computedRevenueStatement.setSubscription(sub);
//            
//            return ResponseEntity.ok(computedRevenueStatement);
//        } catch (Exception e) {
//           logger.error(e.getMessage(), e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }
    

}