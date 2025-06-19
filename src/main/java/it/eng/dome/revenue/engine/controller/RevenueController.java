package it.eng.dome.revenue.engine.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.eng.dome.brokerage.billing.dto.BillingRequestDTO;

@RestController
@RequestMapping("/revenue")
@Tag(name = "Revenue Controller", description = "APIs to manage the calculation og the bills")
public class RevenueController {
	
	protected final Logger logger = LoggerFactory.getLogger(RevenueController.class);
    
    @RequestMapping(value = "/xxx", method = RequestMethod.POST, produces = "application/json", consumes = "application/json")
    public ResponseEntity<String> calculateBill(@RequestBody BillingRequestDTO billRequestDTO) throws Throwable {
		logger.info("Received request for xxx");

		//TODO implemented 
		return new ResponseEntity<String>("{'name' : 'polli'}", HttpStatus.OK);	 
	}
    
 

}
