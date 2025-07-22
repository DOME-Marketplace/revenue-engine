package it.eng.dome.revenue.engine.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.service.PlanService;

@RestController
//@RequiredArgsConstructor
public class PlansController {
    
    private final PlanService subscriptionPlanService;

    public PlansController(PlanService subscriptionPlanService) {
        this.subscriptionPlanService = subscriptionPlanService;
    }
    
    @GetMapping("/plans/{id}")
    public ResponseEntity<Plan> getPlanById(@PathVariable String id) {
        try {
            Plan plan = subscriptionPlanService.findPlanById(id);
            return ResponseEntity.ok(plan);
        } catch (IOException e) {
            return ResponseEntity.notFound().build(); 
        }
    }
    
    @GetMapping("/plans")
    public ResponseEntity<List<Plan>> getAllPlans() {
        try {
            List<Plan> plans = subscriptionPlanService.loadAllPlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


}