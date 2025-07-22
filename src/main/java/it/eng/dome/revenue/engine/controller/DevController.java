package it.eng.dome.revenue.engine.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.service.PlanService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;

@RestController
//@RequiredArgsConstructor
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

    public DevController() {
    }
    
//    @PostMapping("dev/to-acbr")
//    public ResponseEntity<AppliedCustomerBillingRate> convertToACBR(@RequestBody RevenueStatement revenueStatement) {
//        AppliedCustomerBillingRate acbr = RevenueBillingMapper.toACBR(revenueStatement);
//        return ResponseEntity.ok(acbr);
//    }    
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