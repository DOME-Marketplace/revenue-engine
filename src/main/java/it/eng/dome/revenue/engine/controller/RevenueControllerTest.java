package it.eng.dome.revenue.engine.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.model.SubscriptionPlan;
import it.eng.dome.revenue.engine.service.SubscriptionPlanLoader;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RevenueControllerTest {

    private final SubscriptionPlanLoader loader = new SubscriptionPlanLoader();


    @GetMapping("/api/plan/{id}")
    public ResponseEntity<SubscriptionPlan> getPlanById(@PathVariable String id) throws Exception {
        String fileName = "sample_data/" + id + ".json";
        SubscriptionPlan plan = loader.loadFromClasspath(fileName);
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/api/plan")
    public ResponseEntity<String> createPlan(@RequestBody SubscriptionPlan plan) {
        return ResponseEntity.ok("Plan received: " + plan.getName());
    }

    
    
}