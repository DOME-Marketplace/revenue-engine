package it.eng.dome.revenue.engine.controller;

import java.util.ArrayList;
import java.util.List;

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

    public DevOrganizationController() {
        // Constructor for dependency injection
    }

    @GetMapping("{referrerOrganizationId}/referrals")
    public ResponseEntity<List<Organization>> listReferralsProviders(@PathVariable String referrerOrganizationId) {
//        logger.info("Request received: list referrals for referrerOrganizationId {}", referrerOrganizationId);
        try {
            List<Organization> referrals = tmfDataRetriever.listReferralsProviders(referrerOrganizationId);

            if (referrals == null || referrals.isEmpty()) {
                logger.info("No referrals found for Organization with ID {}", referrerOrganizationId);
                return ResponseEntity.ok(new ArrayList<>());
            }

            return ResponseEntity.ok(referrals);
        } catch (Exception e) {
            logger.error("Error retrieving referrals for Organization with ID {}: {}", referrerOrganizationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("{referralOrganizationId}/referrer")
    public ResponseEntity<Organization> getReferrerProvider(@PathVariable String referralOrganizationId) {
//        logger.info("Request received: get referrer for referralOrganizationId {}", referralOrganizationId);

        try {
            Organization referrer = tmfDataRetriever.getReferrerProvider(referralOrganizationId);

            if (referrer == null) {
                logger.info("Referrer not found for Organization with ID {}", referralOrganizationId);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(referrer);
        } catch (Exception e) {
            logger.error("Error retrieving referrer for Organization with ID {}: {}", referralOrganizationId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}