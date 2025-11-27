package it.eng.dome.revenue.engine.controller;

import it.eng.dome.revenue.engine.model.RevenueBill;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.BillsService;
import it.eng.dome.revenue.engine.service.cached.CachedStatementsService;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("revenue/subscriptions")
public class SubscriptionsController {

	protected final Logger logger = LoggerFactory.getLogger(SubscriptionsController.class);

	@Autowired
	private CachedSubscriptionService subscriptionService;

	@Autowired
	private CachedStatementsService statementsService;

	@Autowired
	private BillsService billsService;

	public SubscriptionsController() {
	}

	@GetMapping("")
	public ResponseEntity<List<Subscription>> getAllSubscriptions() {
	    try {
	        List<Subscription> subscriptions = subscriptionService.getAllSubscriptions();

	        if (subscriptions == null || subscriptions.isEmpty()) {
	            logger.info("No subscriptions found");
	            return ResponseEntity.noContent().build();
	        }

	        return ResponseEntity.ok(subscriptions);
	    } catch (Exception e) {
	        logger.error("Error retrieving subscriptions: {}", e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	    }
	}

	@GetMapping("/{subscriptionId}")
	public ResponseEntity<Subscription> getSubscription(@PathVariable String subscriptionId) {
	    try {
	        Subscription subscription = subscriptionService.getSubscriptionByProductId(subscriptionId);

	        if (subscription == null) {
	            logger.warn("Subscription not found for ID {}", subscriptionId);
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
	        }

	        return ResponseEntity.ok(subscription);
	    } catch (Exception e) {
	        logger.error("Error retrieving subscription {}: {}", subscriptionId, e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	    }
	}

	@GetMapping("{subscriptionId}/statements")
	public ResponseEntity<List<RevenueStatement>> statementCalculator(@PathVariable String subscriptionId) {
	    try {
	        List<RevenueStatement> statements = statementsService.getStatementsForSubscription(subscriptionId);

	        if (statements == null || statements.isEmpty()) {
	            logger.info("No statements found for subscription {}", subscriptionId);
				return ResponseEntity.ok(new ArrayList<>());
	        }

	        return ResponseEntity.ok(statements);
	    } catch (Exception e) {
	        logger.error("Failed to retrieve statements for subscription {}: {}", subscriptionId, e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	    }
	}

	@GetMapping("{subscriptionId}/statements/itemsonly")
	public ResponseEntity<List<RevenueItem>> statementItems(@PathVariable String subscriptionId) {
		try {
	        List<RevenueItem> items = statementsService.getItemsForSubscription(subscriptionId);

	        if (items == null || items.isEmpty()) {
	            logger.info("No revenue items found for subscription {}", subscriptionId);
				return ResponseEntity.ok(new ArrayList<>());
	        }

	        return ResponseEntity.ok(items);
	    } catch (Exception e) {
	        logger.error("Failed to retrieve revenue items for subscription {}: {}", subscriptionId, e.getMessage(), e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	    }
	}

	@GetMapping("{subscriptionId}/bills")
	public ResponseEntity<List<RevenueBill>> getBillPeriods(@PathVariable String subscriptionId) {
	    try {
	        List<RevenueBill> bills = billsService.getSubscriptionBills(subscriptionId);

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
}