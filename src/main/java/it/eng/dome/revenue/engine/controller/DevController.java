package it.eng.dome.revenue.engine.controller;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RecurringPeriod;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionPlan;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.service.SubscriptionPlanService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;

@RestController
//@RequiredArgsConstructor
public class DevController {
    
	protected final Logger logger = LoggerFactory.getLogger(DevController.class);

		@Autowired
	PriceCalculator priceCalculator;

	@Autowired
	SubscriptionService subscriptionService;

	@Autowired
	SubscriptionPlanService subscriptionPlanService;
	
	@Autowired
    TmfDataRetriever tmfDataRetriever;

    @Autowired
    public DevController() {
    }

    
    
    @GetMapping("/dev/priceCalculator/{id}")
    public ResponseEntity<Double> priceCalculator(@PathVariable String id) {

    	
        try {
            Subscription sub = subscriptionService.getBySubscriptionId(id);
            logger.info("Subscription: {}", sub);
            
            OffsetDateTime time = OffsetDateTime.now();            
            
            priceCalculator.setSubscription(sub);
                        
            SubscriptionPlan plan = subscriptionPlanService.findPlanById(sub.getPlan().getId());
            logger.info("Plan: {}", plan);
            
            Price price = plan.getPrice();
            
            double computedValue = priceCalculator.compute(price, time);
            
            return ResponseEntity.ok(computedValue);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/dev/bills/")
    public ResponseEntity<List<AppliedCustomerBillingRate>> bills() {
        try {
            List<AppliedCustomerBillingRate> bills = tmfDataRetriever.retrieveBillsInLastMonth();
            return ResponseEntity.ok(bills);
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/dev/bills/{sellerId}")
    public ResponseEntity<List<AppliedCustomerBillingRate>> sellerBills(@PathVariable String sellerId) {
        try {
            List<AppliedCustomerBillingRate> bills = tmfDataRetriever.retrieveBillsForSellerInLastMonth(sellerId);
            return ResponseEntity.ok(bills);
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
             return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/dev/revenue/subscriptions/{subscriptionId}/statements")
    public ResponseEntity<List<RevenueStatement>> sellerStatements(@PathVariable String subscriptionId) {
        try {
            List<RevenueStatement> statements = new ArrayList<>();

            Subscription subscription = this.createFakeSubscription(subscriptionId);

            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(subscription);

            statements.add(new RevenueStatement(subscription, timeHelper.getSubscriptionPeriodAt(OffsetDateTime.now())));
            statements.add(new RevenueStatement(subscription, timeHelper.getSubscriptionPeriodByOffset(OffsetDateTime.now(), -1)));
            statements.add(new RevenueStatement(subscription, timeHelper.getSubscriptionPeriodByOffset(OffsetDateTime.now(), -2)));
            statements.add(new RevenueStatement(subscription, timeHelper.getSubscriptionPeriodByOffset(OffsetDateTime.now(), -3)));


            return ResponseEntity.ok(statements);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Subscription createFakeSubscription(String subscriptionId) {
        Subscription subscription = new Subscription();
        subscription.setName("Test Subscription");
        subscription.setStatus("ACTIVE");
        subscription.setStartDate(OffsetDateTime.now().minusMonths(47));
        subscription.setPlan(this.createFakeSubscriptionPlan(subscriptionId));
        subscription.setRelatedParties(new ArrayList<>());

        return subscription;
    }

    private SubscriptionPlan createFakeSubscriptionPlan(String planId) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(planId);
        plan.setName("Test Plan");
        plan.setDescription("This is a test subscription plan.");
        plan.setContractDurationPeriodType(RecurringPeriod.YEAR);
        plan.setContractDurationLength(1);
        return plan;
    }
    

}