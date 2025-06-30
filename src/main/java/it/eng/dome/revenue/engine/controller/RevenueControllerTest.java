package it.eng.dome.revenue.engine.controller;




import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

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

	@Autowired
    private final SubscriptionPlanLoader loader = new SubscriptionPlanLoader();

    private static final String SAVE_PATH = "src/main/resources/sample_data/";

    @GetMapping("/api/plan/{id}")
    public ResponseEntity<SubscriptionPlan> getPlanById(@PathVariable String id) throws Exception {
        String fileName = "sample_data/" + id + ".json";
        SubscriptionPlan plan = loader.loadFromClasspath(fileName);
        return ResponseEntity.ok(plan);
    }
    
    @GetMapping("/api/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        try {
            List<SubscriptionPlan> plans = loader.loadAllPlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /*@PostMapping(value = "/api/plan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createPlan(@RequestBody SubscriptionPlan plan) {
    	
        return ResponseEntity.ok("Plan received: " + plan.getName());
    }*/
    

    @PostMapping(value = "/api/plan", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> savePlan(@RequestBody String rawJson) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = "plan_" + timestamp + ".json";
            
            String savedPath = loader.saveRawJson(rawJson, filename);
            
            return ResponseEntity.ok("""
                {
                    "status": "success",
                    "filename": "%s",
                    
                }
                """.formatted(filename));
                
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("""
                {
                    "status": "error",
                    "message": "%s"
                }
                """.formatted(e.getMessage()));
        }
    }
}