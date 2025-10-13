package it.eng.dome.revenue.engine.controller;

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

import it.eng.dome.revenue.engine.model.RevenueBill;
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
    public ResponseEntity<RevenueBill> getBillPeriods(@PathVariable String billId) {
        try {
            RevenueBill bill = billsService.getRevenueBillById(billId);
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
    
    @GetMapping("{revenueBillId}/cb")
    public ResponseEntity<CustomerBill> getCustomerBillByRevenueBillId(@PathVariable String revenueBillId) {
        try {
            CustomerBill cb = billsService.getCustomerBillByRevenueBillId(revenueBillId);
            if (cb == null) {
                logger.info("No CustomerBill found for RevenueBillId: {}", revenueBillId);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(cb);
        }
        catch(Exception e) {
            logger.error("Failed to generate cb for{}: {}", revenueBillId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("{revenueBillId}/acbr")
    public ResponseEntity<List<AppliedCustomerBillingRate>> getACBRsByRevenueBillId(@PathVariable String revenueBillId) {
    	List<AppliedCustomerBillingRate> acbrs = billsService.getACBRsByRevenueBillId(revenueBillId);
        try {
            if (acbrs == null) {
                logger.info("No Applied Customer Billing Rate found for revenue bill id: {}", revenueBillId);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(acbrs);
        }
        catch(Exception e) {
            logger.error("Failed to generate acbr for{}: {}", revenueBillId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}