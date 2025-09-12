package it.eng.dome.revenue.engine.controller;

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

import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.service.BillsService;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;


@RestController
//@RequiredArgsConstructor
@RequestMapping("revenue/bills")
public class BillsController {
    
	protected final Logger logger = LoggerFactory.getLogger(BillsController.class);

    @Autowired
	private BillsService billsService;

    public BillsController() {
    }

    @GetMapping("{billId}")
    public ResponseEntity<SimpleBill> getBillPeriods(@PathVariable String billId) {
//        logger.info("Request received: fetch bill with ID {}", billId);
        try {
            SimpleBill bill = billsService.getSimpleBillById(billId);

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
    
    @GetMapping("{simpleBillId}/cb")
    public ResponseEntity<CustomerBill> getCustomerBillBySimpleBillId(@PathVariable String simpleBillId) {
        CustomerBill cb = billsService.getCustomerBillBySimpleBillId(simpleBillId);
        
        if (cb == null) {
            logger.info("No CustomerBill found for simpleBillId: {}", simpleBillId);
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(cb);
    }

    // test controller (can be removed)
    @PostMapping("/toCB")
    public ResponseEntity<CustomerBill> getCustomerBillBySimpleBill (@RequestBody SimpleBill sb) {
    	CustomerBill cb = billsService.getCustomerBillBySimpleBill(sb);
        return ResponseEntity.ok(cb);
    }
    
    @GetMapping("{simpleBillId}/acbr")
    public ResponseEntity<List<AppliedCustomerBillingRate>> getACBRsBySimpleBillId(@PathVariable String simpleBillId) {
    	List<AppliedCustomerBillingRate> acbrs = billsService.getACBRsBySimpleBillId(simpleBillId);
    	
    	if (acbrs == null) {
            logger.info("No Applied Customer Billing Rate found for simpleBillId: {}", simpleBillId);
            return ResponseEntity.notFound().build();
        }
           
    	return ResponseEntity.ok(acbrs);
    }
    
    // test controller (can be removed)
    @PostMapping("/acbr")
    public ResponseEntity<List<AppliedCustomerBillingRate>> getACBRsBySimpleBill (@RequestBody SimpleBill sb) {
    	List<AppliedCustomerBillingRate> acbrList = billsService.getACBRsBySimpleBill(sb);
        return ResponseEntity.ok(acbrList);
    }
}