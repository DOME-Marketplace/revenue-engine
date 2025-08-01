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

import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.BillsService;
import it.eng.dome.revenue.engine.service.StatementsService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;

@RestController
@RequestMapping("revenue/subscriptions")
public class SubscriptionsController {
    
	protected final Logger logger = LoggerFactory.getLogger(StatementsController.class);

	@Autowired
	private SubscriptionService subscriptionService;

    @Autowired
	private StatementsService statementsService;

    @Autowired
	private BillsService billsService;

	@Autowired
	TmfDataRetriever tmfDataRetriever;

    public SubscriptionsController() {
    }

    @GetMapping("")
    public ResponseEntity<List<Subscription>> getAllSubscriptions() {
        try {
            List<Subscription> subscriptions = subscriptionService.getAllSubscriptions();
            return ResponseEntity.ok(subscriptions);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<Subscription> getSubscription(@PathVariable String subscriptionId) {
        try {
            Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
            if (subscription == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            else {
                return ResponseEntity.ok(subscription);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("{subscriptionId}/statements")
    public ResponseEntity<List<RevenueStatement>> statementCalculator(@PathVariable String subscriptionId) {    	   
        try {
            return ResponseEntity.ok(this.statementsService.getStatementsForSubscription(subscriptionId));
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

    @GetMapping("{subscriptionId}/statements/itemsonly")
    public ResponseEntity<List<RevenueItem>> statementItems(@PathVariable String subscriptionId) {    	
        try {
            return ResponseEntity.ok(this.statementsService.getItemsForSubscription(subscriptionId));
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("{subscriptionId}/bills")
    public ResponseEntity<List<SimpleBill>> getBillPeriods(@PathVariable String subscriptionId) {    	   
        try {
            return ResponseEntity.ok(this.billsService.getSubscriptionBills(subscriptionId));
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }    

}