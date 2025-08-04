package it.eng.dome.revenue.engine.controller;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.model.Report;
import it.eng.dome.revenue.engine.service.ReportingService;
import it.eng.dome.tmforum.tmf632.v4.ApiException;

@RestController
@RequestMapping("/revenue/dashboard")
public class ReportingController {
	
	protected final Logger logger = LoggerFactory.getLogger(ReportingController.class);

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/{relatedPartyId}")
    public ResponseEntity<List<Report>> getDashboard(@PathVariable String relatedPartyId) {
        logger.info("Request received: reporting for dashboard, Organization ID = {}", relatedPartyId);

        try {
            List<Report> reports = reportingService.getDashboardReport(relatedPartyId);

            if (reports == null || reports.isEmpty()) {
                logger.warn("No reports found for Organization with ID {}", relatedPartyId);
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(reports);
        } catch (ApiException | IOException e) {
            logger.error("Failed to generate dashboard report for Organization with ID {}: {}", relatedPartyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
