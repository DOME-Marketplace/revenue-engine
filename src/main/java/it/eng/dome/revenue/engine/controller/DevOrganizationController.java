package it.eng.dome.revenue.engine.controller;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;

@RestController
@RequestMapping("/dev2/revenue/organizations")
public class DevOrganizationController {
    
	protected final Logger logger = LoggerFactory.getLogger(DevOrganizationController.class);

	@Autowired
    TmfDataRetriever tmfDataRetriever;


    @Autowired
    public DevOrganizationController() {
        // Constructor for dependency injection
    }

    @GetMapping("{referrerOrganizationId}/referrals")
    public ResponseEntity<List<Organization>> listReferralsProviders(@PathVariable String referrerOrganizationId) {
        try {
            return ResponseEntity.ok(tmfDataRetriever.listReferralsProviders(referrerOrganizationId));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("{referralOrganizationId}/referrer")
    public ResponseEntity<Organization> getReferrerProvider(@PathVariable String referralOrganizationId) {
        try {
            return ResponseEntity.ok(tmfDataRetriever.getReferrerProvider(referralOrganizationId));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}