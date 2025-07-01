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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.dome.revenue.engine.model.SubscriptionActive;
import it.eng.dome.revenue.engine.model.SubscriptionPlan;
import it.eng.dome.revenue.engine.service.SubscriptionActiveService;
import it.eng.dome.revenue.engine.service.SubscriptionPlanService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
//@RequiredArgsConstructor
public class DevController {
    

	@Autowired
    TmfDataRetriever tmfDataRetriever;

    @Autowired
    public DevController(SubscriptionPlanService subscriptionPlanService,
                              SubscriptionActiveService subscriptionActiveService,
                              ObjectMapper mapper) {
    }

    @GetMapping("/dev/bills/")
    public ResponseEntity<List<AppliedCustomerBillingRate>> bills() {
        try {
            List<AppliedCustomerBillingRate> bills = tmfDataRetriever.retrieveBillsInLastMonth();
            return ResponseEntity.ok(bills);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/dev/bills/{sellerId}")
    public ResponseEntity<List<AppliedCustomerBillingRate>> sellerBills(@PathVariable String sellerId) {
        try {
            List<AppliedCustomerBillingRate> bills = tmfDataRetriever.retrieveBillsForSellerInLastMonth(sellerId);
            return ResponseEntity.ok(bills);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
}