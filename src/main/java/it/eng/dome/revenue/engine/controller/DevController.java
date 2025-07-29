package it.eng.dome.revenue.engine.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.service.PlanService;
import it.eng.dome.revenue.engine.service.RevenueService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;

@RestController
//@RequiredArgsConstructor
@RequestMapping("/dev/revenue")
public class DevController {
    
	protected final Logger logger = LoggerFactory.getLogger(DevController.class);

	@Autowired
	PriceCalculator priceCalculator;

	@Autowired
	SubscriptionService subscriptionService;

	@Autowired
	PlanService subscriptionPlanService;
	
	@Autowired
    TmfDataRetriever tmfDataRetriever;
	
	@Autowired
    RevenueService revenueService;

    public DevController() {
    }
    
    @GetMapping("/billingAccount/{relatedPartyId}")
    public ResponseEntity<BillingAccountRef> getBillingAccountByRelatedParty(@PathVariable String relatedPartyId) {
        try {
            BillingAccountRef ref = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(relatedPartyId);
            return ResponseEntity.ok(ref);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    
    @PostMapping("/to-acbr")
    public ResponseEntity<AppliedCustomerBillingRate> convertToACBR(@RequestBody RevenueStatement revenueStatement) {    	
//    	//TODO: verificare quale RelatedParty prendere fra quelli presenti
//    	BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(revenueStatement.getSubscription().getRelatedParties().get(0).getId());
        AppliedCustomerBillingRate acbr = revenueService.buildACBR(revenueStatement);
        return ResponseEntity.ok(acbr);
    }
    
    @PostMapping("/to-acbr-list")
    public ResponseEntity<List<AppliedCustomerBillingRate>> convertToACBRList(@RequestBody List<RevenueStatement> revenueStatements) {
        List<AppliedCustomerBillingRate> acbrList = revenueStatements.stream()
            .map(revenueService::buildACBR)
            .toList();
        return ResponseEntity.ok(acbrList);
    }

//    
//    @GetMapping("/dev/referrals/{referrerId}")
//    public ResponseEntity<List<Organization>> getReferrals(@PathVariable String referrerId) {
//        try {
//            List<Organization> referrals = tmfDataRetriever.listReferralsProviders(referrerId);
//            return ResponseEntity.ok(referrals);
//        } catch (Exception e) {
//            logger.error("Error in getReferrals", e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        }
//    }
    

}