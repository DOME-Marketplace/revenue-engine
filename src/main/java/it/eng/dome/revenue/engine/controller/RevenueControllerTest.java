package it.eng.dome.revenue.engine.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.dome.revenue.engine.model.SubscriptionActive;
import it.eng.dome.revenue.engine.model.SubscriptionPlan;
import it.eng.dome.revenue.engine.service.SubscriptionActiveService;
import it.eng.dome.revenue.engine.service.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RevenueControllerTest {
    
    private final SubscriptionPlanService subscriptionPlanService;
    private final SubscriptionActiveService subscriptionActiveService;
    private final ObjectMapper mapper;

    @Autowired
    public RevenueControllerTest(SubscriptionPlanService subscriptionPlanService,
                              SubscriptionActiveService subscriptionActiveService,
                              ObjectMapper mapper) {
        this.subscriptionPlanService = subscriptionPlanService;
        this.subscriptionActiveService = subscriptionActiveService;
        this.mapper = mapper;
    }

    // ===== SubscriptionPlan Endpoints =====
    @GetMapping("/plan/filename/{filename}")
    public ResponseEntity<SubscriptionPlan> getPlanByFileName(@PathVariable String filename) {
        try {
            String fileName = "sample_data/" + filename + ".json";
            SubscriptionPlan plan = subscriptionPlanService.loadFromClasspath(fileName);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/plan/id/{id}")
    public ResponseEntity<SubscriptionPlan> getPlanById(@PathVariable String id) {
        try {
            SubscriptionPlan plan = subscriptionPlanService.findPlanById(id);
            return ResponseEntity.ok(plan);
        } catch (IOException e) {
            return ResponseEntity.notFound().build(); 
        }
    }
    
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        try {
            List<SubscriptionPlan> plans = subscriptionPlanService.loadAllPlans();
            return ResponseEntity.ok(plans);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

//    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<?> savePlan(@RequestBody @Valid SubscriptionPlan plan) {
//        try {
//            String filename = "plan_" + System.currentTimeMillis() + ".json";
//            String json = mapper.writeValueAsString(plan);
//            subscriptionPlanService.saveRawJson(json, filename);
//
//            return ResponseEntity.ok(Map.of(
//                "status", "success",
//                "filename", filename
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(Map.of(
//                        "status", "error",
//                        "message", e.getMessage()
//                    ));
//        }
//    }
//    
//    @DeleteMapping("/plan/delete/{planName}")
//    public ResponseEntity<Void> deletePlan(@PathVariable("planName") String planName) {
//        try {
//        	subscriptionPlanService.deletePlan(planName);
//            return ResponseEntity.noContent().build();
//        } catch (IOException e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//    
//
//    @PutMapping("/plan/update/{planName}")
//    public String updateSubscription(@PathVariable String planName,
//    		@RequestBody SubscriptionPlan subscription) throws IOException {
//
//        return subscriptionPlanService.updatePlan(planName, subscription);
//    }
    
    
    // ===== SubscriptionActive Endpoints =====
    @GetMapping("/subscription/filename/{filename}")
    public ResponseEntity<SubscriptionActive> getSubscriptionByFileName(@PathVariable String filename) {
        try {
            String fileName = "sample_data/sub/" + filename + ".json";
            SubscriptionActive subscription = subscriptionActiveService.loadFromClasspath(fileName);
            return ResponseEntity.ok(subscription);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<SubscriptionActive>> getAllSubscriptions() {
        try {
            List<SubscriptionActive> subscriptions = subscriptionActiveService.loadAllFromStorage();
            return ResponseEntity.ok(subscriptions);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    
//    @PostMapping(value = "/subscription", consumes = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<?> saveSubscription(@RequestBody @Valid SubscriptionActive subscription) {
//        try {
//            String filename = "subscription_" + System.currentTimeMillis() + ".json";
//            String json = mapper.writeValueAsString(subscription);
//            subscriptionActiveService.saveRawJsonToStorage(json, filename);
//
//            return ResponseEntity.ok(Map.of(
//                "status", "success",
//                "filename", filename
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(Map.of(
//                    "status", "error",
//                    "message", e.getMessage()
//                ));
//        }
//    }

    @GetMapping("/subscription/party-id/{partyId}")
    public ResponseEntity<List<SubscriptionActive>> getSubscriptionsByPartyId(
            @PathVariable String partyId) {
        try {
            List<SubscriptionActive> subscriptions = 
                subscriptionActiveService.getByRelatedPartyId(partyId);
            
            return subscriptions.isEmpty() 
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(subscriptions);
                
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
//   @DeleteMapping("/subscription/delete/{subName}")
//    public ResponseEntity<Void> deleteSub(@PathVariable("subName") String subName) {
//        try {
//        	subscriptionPlanService.deletePlan(subName);
//            return ResponseEntity.noContent().build();
//        } catch (IOException e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//    
//    @PutMapping("/subscription/update/{subName}")
//    public String updateSubscription(
//            @PathVariable String subName,
//            @RequestBody SubscriptionActive subscription) throws IOException {
//
//        return subscriptionActiveService.updateSubscription(subName, subscription);
//    }
}