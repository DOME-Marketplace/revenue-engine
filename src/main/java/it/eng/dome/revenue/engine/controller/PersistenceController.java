package it.eng.dome.revenue.engine.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import it.eng.dome.revenue.engine.service.TmfPeristenceService;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

@RestController
//@RequiredArgsConstructor
@RequestMapping("/dev/revenue/persistence")
public class PersistenceController {
    
	protected final Logger logger = LoggerFactory.getLogger(PersistenceController.class);
	
    @Autowired
    private TmfPeristenceService tmfPersistenceService;

    public PersistenceController() {
    }

    // request persistence of everything
    @GetMapping("persist")
    public ResponseEntity<List<CustomerBill>> peristEverything() {
        try {
            List<CustomerBill> bills = this.tmfPersistenceService.persistAllRevenueBills();
            return ResponseEntity.ok(bills);
        } catch (Exception e) {
            logger.error("Failed to persist customerbills on tmf: {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    } 

    // request persistence of bills for a given provider
    @GetMapping("persist/provider/{providerId}")
    public ResponseEntity<List<CustomerBill>> persistForProvider(@PathVariable String providerId) {
        try {
            List<CustomerBill> bills = this.tmfPersistenceService.persistProviderRevenueBills(providerId);
            return ResponseEntity.ok(bills);
        } catch (Exception e) {
            logger.error("Failed to persist customerbills on tmf: {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    } 

    // request persistence of bills for a given subscription
    @GetMapping("persist/subscription/{subscriptionId}")
    public ResponseEntity<List<CustomerBill>> persistForSubscription(@PathVariable String subscriptionId) {
        try {
            List<CustomerBill> bills = this.tmfPersistenceService.persistSubscriptionRevenueBills(subscriptionId);
            return ResponseEntity.ok(bills);
        } catch (Exception e) {
            logger.error("Failed to persist customerbills on tmf: {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    } 

    // request persistence of bills for a given provider
    @GetMapping("persist/revenuebill/{revenueBillId}")
    public ResponseEntity<CustomerBill> persistRevenueBill(@PathVariable String revenueBillId) {
        try {
            CustomerBill cb = this.tmfPersistenceService.persistRevenueBill(revenueBillId);
            return ResponseEntity.ok(cb);
        } catch (Exception e) {
            logger.error("Failed to persist customerbills on tmf: {} {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}