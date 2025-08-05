package it.eng.dome.revenue.engine.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import it.eng.dome.revenue.engine.scheduler.Scheduler;

@RestController
@RequestMapping("/revenue/scheduler")
public class SchedulerController {

    @Autowired
    private Scheduler scheduler;

    @PostMapping("/run-now")
    public ResponseEntity<String> startScheduler() {
        try {
            scheduler.runScheduler();
            return ResponseEntity.ok("Scheduler process triggered successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error scheduler: " + e.getMessage());
        }
    }
}
