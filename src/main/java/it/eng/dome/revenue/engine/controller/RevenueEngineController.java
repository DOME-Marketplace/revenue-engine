package it.eng.dome.revenue.engine.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.eng.dome.revenue.engine.service.HealthService;
import it.eng.dome.revenue.engine.utils.health.Health;
import it.eng.dome.revenue.engine.utils.health.Info;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/revenue")
@Tag(name = "Revenue Engine Controller", description = "APIs to manage the revenue-engine")
public class RevenueEngineController {

    private static final Logger log = LoggerFactory.getLogger(RevenueEngineController.class);
    
    @Autowired
    private HealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<Health> getHealth() {
        try {
            Health health = this.healthService.getHealth();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/info")
    public ResponseEntity<Info> getInfp() {
        try {
            Info info = this.healthService.getInfo();
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}