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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.service.BillsService;
import it.eng.dome.revenue.engine.service.PlanService;
import it.eng.dome.revenue.engine.service.RevenueService;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
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
	CachedSubscriptionService subscriptionService;

	@Autowired
	PlanService subscriptionPlanService;
	
	@Autowired
    TmfDataRetriever tmfDataRetriever;
	
	@Autowired
    RevenueService revenueService;
	
	@Autowired
    private BillsService billsService;
	
	@Autowired
	private PlanService planService;

    public DevController() {
    }

    /* PF 10 Sep commented out
    @GetMapping("/Offering/{offeringId}")
    public ResponseEntity<Plan> getPlanByPoId(@PathVariable String offeringId) {
        try {
            Plan plan = planService.findPlanByOfferingId(offeringId);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    } 
         */
    

    @GetMapping("/subscriptions/{subscriptionId}/bills")
	public ResponseEntity<List<SimpleBill>> getBillPeriods(@PathVariable String subscriptionId) {
	    try {
	        List<SimpleBill> bills = billsService.getSubscriptionBills(subscriptionId);

	        if (bills == null || bills.isEmpty()) {
	            logger.info("No bills found for subscription {}", subscriptionId);
				return ResponseEntity.ok(new ArrayList<>());
	        }

	        return ResponseEntity.ok(bills);
	    } catch (Exception e) {
	        logger.error("Failed to retrieve bills for subscription {}: {}", subscriptionId, e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	    }
	}

    @GetMapping("bills/{billId}")
    public ResponseEntity<SimpleBill> getBill(@PathVariable String billId) {
        try {
            SimpleBill bill = billsService.getBill(billId);

            if (bill != null) {
                return ResponseEntity.ok(bill);
            } else {
                logger.info("Bill not found for ID {}", billId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve bill with ID {}: {}", billId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    } 

    @GetMapping("bills/{simpleBillId}/acbr")
    public ResponseEntity<List<AppliedCustomerBillingRate>> getABCRList(@PathVariable String simpleBillId) {
        SimpleBill sb;
        try {
            sb = billsService.getBill(simpleBillId);
            if (sb == null) {
                logger.info("No Simple Bill found for ID {}", simpleBillId);
                return ResponseEntity.noContent().build();
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve Simple Bill with ID {}: {}", simpleBillId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        try {
            List<AppliedCustomerBillingRate> acbrList = billsService.buildABCRList(sb);

            // now we have the list of acbr. Extract the product
            // let's ask the billing service to enrich with taxes
            acbrList = billsService.applyTaxes(acbrList);

            logger.debug("found {} acbrs", acbrList.size());

            return ResponseEntity.ok(acbrList);
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            logger.warn("Invalid Simple Bill data for ID {}: {}", simpleBillId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to build ACBR List from Simple Bill with ID {}: {}", simpleBillId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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