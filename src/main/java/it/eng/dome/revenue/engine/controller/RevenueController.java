package it.eng.dome.revenue.engine.controller;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.eng.dome.revenue.engine.service.RevenueService;
import it.eng.dome.tmforum.tmf622.v4.JSON;


@RestController
@RequestMapping("/revenue")
@Tag(name = "Revenue Controller", description = "APIs to manage the calculation og the bills")
public class RevenueController {
	
	protected final Logger logger = LoggerFactory.getLogger(RevenueController.class);
	
	@Autowired
	private RevenueService revenueService;
    
    @RequestMapping(value = "/test", method = RequestMethod.POST, produces = "application/json", consumes = "application/json")
    public ResponseEntity<String> testServices(@RequestBody String payload) throws Throwable {
		logger.info("Received request: {}", payload);

		//TEST customers
		revenueService.getCustomers();
		
		//TEST agreements
		revenueService.getAgreements();

		//TODO implemented
		Map<String, String> map = new HashMap<String, String>();
        map.put("msg", "Get customer + agreement");
        
		return new ResponseEntity<String>(JSON.getGson().toJson(map), HttpStatus.OK);	 
	}
    
 

}
