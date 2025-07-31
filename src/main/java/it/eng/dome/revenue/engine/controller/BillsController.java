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

import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.service.BillsService;


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
        try {
            SimpleBill bill = this.billsService.getBill(billId);
            if(bill!=null)
                return ResponseEntity.ok(this.billsService.getBill(billId));
            else
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

}