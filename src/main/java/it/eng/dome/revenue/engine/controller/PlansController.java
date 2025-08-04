package it.eng.dome.revenue.engine.controller;

import java.io.IOException;
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
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.PlanService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.service.validation.PlanValidationReport;

@RestController
@RequestMapping("/revenue/plans")
public class PlansController {
	
	private static final Logger logger = LoggerFactory.getLogger(PlansController.class);
    
	@Autowired
    private PlanService subscriptionPlanService;
    
    @Autowired
	private SubscriptionService subscriptionService;

   
    @GetMapping("")
    public ResponseEntity<List<Plan>> getAllPlans() {
//    	logger.info("Request received: fetch all plans");    	
        try {
            List<Plan> plans = subscriptionPlanService.loadAllPlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
        	logger.error("Failed to load plans: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{planId}")
    public ResponseEntity<Plan> getPlanById(@PathVariable String planId) {
//    	logger.info("Request received: fetch plan with ID {}", planId);
        try {
            Plan plan = subscriptionPlanService.findPlanById(planId);
            return ResponseEntity.ok(plan);
        } catch (IOException e) {
            logger.warn(e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving plan {}: {}", planId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{planId}/subscriptions")
    public ResponseEntity<List<Subscription>> getSubscriptionsByPlanId(@PathVariable String planId) {
//        logger.info("Request received: fetch subscriptions for planId {}", planId);
        try {
            List<Subscription> subscriptions = subscriptionService.getByPlanId(planId);

            if (subscriptions == null || subscriptions.isEmpty()) {
                logger.info("No subscriptions found for paln with ID {}", planId);
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(subscriptions);
        } catch (Exception e) {
            logger.error("Failed to retrieve subscriptions for planId {}: {}", planId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{planId}/validate")
    public ResponseEntity<PlanValidationReport> validatePlan(@PathVariable String planId) {
        try {
            PlanValidationReport report = subscriptionPlanService.validatePlan(planId);
            return ResponseEntity.ok(report);
        } catch (IOException e) {
            logger.error("Plan not found or IO error while validating plan with ID {}: {}", planId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.error("Unexpected error validating plan with ID {}: {}", planId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


}