package it.eng.dome.revenue.engine.controller;

import java.io.IOException;
import java.util.List;

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

@RestController
//@RequiredArgsConstructor
@RequestMapping("/revenue/plans")
public class PlansController {
    
	@Autowired
    private PlanService subscriptionPlanService;
    
    @Autowired
	private SubscriptionService subscriptionService;

    public PlansController() {
    }
    
    @GetMapping("")
    public ResponseEntity<List<Plan>> getAllPlans() {
        try {
            List<Plan> plans = subscriptionPlanService.loadAllPlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{planId}")
    public ResponseEntity<Plan> getPlanById(@PathVariable String planId) {
        try {
            Plan plan = subscriptionPlanService.findPlanById(planId);
            return ResponseEntity.ok(plan);
        } catch (IOException e) {
            return ResponseEntity.notFound().build(); 
        }
    }
    
    @GetMapping("/{planId}/subscriptions")
    public ResponseEntity<List<Subscription>> getSubscriptionsByPlanId(@PathVariable("planId") String planId) {
        List<Subscription> subscriptions = subscriptionService.getByPlanId(planId);
        if (subscriptions == null || subscriptions.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(subscriptions);
    }
    
}