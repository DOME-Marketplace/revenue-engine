package it.eng.dome.revenue.engine.controller;

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
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
@RequestMapping("/revenue")
@Tag(name = "Revenue Engine Controller", description = "APIs to manage the revenue-engine")
public class RevenueEngineController {

    private static final Logger log = LoggerFactory.getLogger(RevenueEngineController.class);
    
    @Autowired
    private BuildProperties buildProperties;

    @RequestMapping(value = "/info", method = RequestMethod.GET, produces = "application/json")
    @Operation(/*summary = "Get info", description = "Return detail info.",*/ responses = {
        @ApiResponse(/*description = "Example of response",*/
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"name\":\"Revenue Engine\", \"version\":\"0.0.1\", \"release_time\":\"19-06-2025 16:50:34\"}")
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
