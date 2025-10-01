package it.eng.dome.revenue.engine.controller;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.service.HealthService;
import it.eng.dome.revenue.engine.utils.health.Health;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
@RequestMapping("/revenue")
@Tag(name = "Revenue Engine Controller", description = "APIs to manage the revenue-engine")
public class RevenueEngineController {

    private static final Logger log = LoggerFactory.getLogger(RevenueEngineController.class);
    
    @Autowired
    private BuildProperties buildProperties;

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

    @RequestMapping(value = "/info", method = RequestMethod.GET, produces = "application/json")
    @Operation(/*summary = "Get info", description = "Return detail info.",*/ responses = {
        @ApiResponse(/*description = "Example of response",*/
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"name\":\"Revenue Engine\", \"version\":\"0.0.1\", \"release_time\":\"10-07-2025 17:02:56\"}")
            ))
    })

    public Map<String, String> getInfo() {
        log.info("Request getInfo");
        Map<String, String> map = new HashMap<String, String>();
        map.put("version", buildProperties.getVersion());
        map.put("name", buildProperties.getName());
        map.put("release_time", getFormatterTimestamp(buildProperties.getTime()));
        log.debug(map.toString());
        return map;
    }
    
    private String getFormatterTimestamp(Instant time) {
    	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        ZonedDateTime zonedDateTime = time.atZone(ZoneId.of("Europe/Rome"));
    	return zonedDateTime.format(formatter);
    }
}