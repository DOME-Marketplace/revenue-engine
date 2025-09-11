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
    
    @GetMapping("{simpleBillId}/cb")
    public ResponseEntity<CustomerBill> getCustomerBill(@PathVariable String simpleBillId) {
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
            CustomerBill cb = billsService.buildCB(sb);
            return ResponseEntity.ok(cb);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid Simple Bill data for ID {}: {}", simpleBillId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to build Customer Bill from Simple Bill with ID {}: {}", simpleBillId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("{simpleBillId}/acbr")
    public ResponseEntity<List<AppliedCustomerBillingRate>> getABCRList(@PathVariable String simpleBillId) {
        SimpleBill sb;
        try {
            sb = billsService.getBill(simpleBillId);
            if (sb == null) {
                logger.info("No Simple Bill found for ID {}", simpleBillId);
                return ResponseEntity.ok(new ArrayList<>());
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve Simple Bill with ID {}: {}", simpleBillId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        try {
            List<AppliedCustomerBillingRate> acbrList = billsService.getABCRList(sb);
            return ResponseEntity.ok(acbrList);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid Simple Bill data for ID {}: {}", simpleBillId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Failed to build ACBR List from Simple Bill with ID {}: {}", simpleBillId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/to-acbr-list")
    public ResponseEntity<List<AppliedCustomerBillingRate>> getABCRList (@RequestBody SimpleBill sb) {
    	List<AppliedCustomerBillingRate> acbrList = billsService.getABCRList(sb);
        return ResponseEntity.ok(acbrList);
    }
}