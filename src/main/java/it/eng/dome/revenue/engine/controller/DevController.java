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
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.service.BillsService;
import it.eng.dome.revenue.engine.service.PlanService;
import it.eng.dome.revenue.engine.service.RevenueService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

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
	
	@Autowired
    private BillsService billsService;

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
    
    @PostMapping("/toCb")
    public ResponseEntity<CustomerBill> convertToCB(@RequestBody SimpleBill sb) {    	
        CustomerBill cb = billsService.buildCB(sb);
        return ResponseEntity.ok(cb);
    }
    
    @PostMapping("/toCb/list")
    public ResponseEntity<List<CustomerBill>> convertToCBList(@RequestBody List<SimpleBill> sbs) {
        List<CustomerBill> cbList = sbs.stream()
            .map(billsService::buildCB)
            .toList();
        return ResponseEntity.ok(cbList);
    }   
    
    @PostMapping("/to-acbr")
    public ResponseEntity<AppliedCustomerBillingRate> convertToACBR(@RequestBody RevenueStatement revenueStatement) {    	
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

}