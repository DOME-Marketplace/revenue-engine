package it.eng.dome.revenue.engine.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.eng.dome.brokerage.observability.health.Health;
import it.eng.dome.brokerage.observability.info.Info;
import it.eng.dome.revenue.engine.service.cached.CachedHealthService;

@RestController
@RequestMapping("/revenue")
@Tag(name = "Revenue Engine Info Controller", description = "APIs to manage info and health of the revenue-engine")
public class RevenueEngineController {

	private final Logger logger = LoggerFactory.getLogger(RevenueEngineController.class);

    @Autowired
    private CachedHealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<Health> getHealth() {
        try {
            Health health = this.healthService.getHealth();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/info")
    public ResponseEntity<Info> getInfo() {
        try {
            Info info = this.healthService.getInfo();
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}