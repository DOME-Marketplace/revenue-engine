package it.eng.dome.revenue.engine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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


    @GetMapping("/api/plan")
    public ResponseEntity<SubscriptionPlan> getPlan() throws Exception {
        SubscriptionPlan plan = loader.loadFromClasspath("sample_data/BasicPlan.json");
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/api/plan")
    public ResponseEntity<String> createPlan(@RequestBody SubscriptionPlan plan) {
        return ResponseEntity.ok("Plan received: " + plan.getName());
    }
}