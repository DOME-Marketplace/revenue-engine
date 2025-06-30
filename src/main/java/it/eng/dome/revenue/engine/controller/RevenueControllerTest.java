package it.eng.dome.revenue.engine.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.dome.revenue.engine.model.SubscriptionPlan;
import it.eng.dome.revenue.engine.service.SubscriptionPlanLoader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class RevenueControllerTest {
	
	private final SubscriptionPlanLoader loader;
    private final ObjectMapper mapper;
    
    @Autowired
    public RevenueControllerTest(SubscriptionPlanLoader loader, ObjectMapper mapper) {
        this.loader = loader;
        this.mapper = mapper;
    }

	 @GetMapping("/plan/{id}")
    public ResponseEntity<SubscriptionPlan> getPlanById(@PathVariable String id) {
        try {
            String fileName = "sample_data/" + id + ".json";
            SubscriptionPlan plan = loader.loadFromClasspath(fileName);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

	 @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        try {
            List<SubscriptionPlan> plans = loader.loadAllPlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

	 @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE)
	    public ResponseEntity<?> savePlan(@RequestBody @Valid SubscriptionPlan plan) {
	        try {
	            String filename = "TestPlan.json";

	            String json = mapper.writeValueAsString(plan);
	            loader.saveRawJson(json, filename);

	            return ResponseEntity.ok(Map.of(
	                "status", "success",
	                "filename", filename
	            ));
	        } catch (Exception e) {
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                    .body(Map.of(
	                        "status", "error",
	                        "message", e.getMessage()
	                    ));
	        }
    }
}
