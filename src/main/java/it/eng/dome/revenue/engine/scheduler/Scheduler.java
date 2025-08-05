package it.eng.dome.revenue.engine.scheduler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.BillsService;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import jakarta.annotation.PostConstruct;

@Component
public class Scheduler {

    private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private BillsService billsService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String OUTPUT_FOLDER = "src/main/resources/data";
    private static final String OUTPUT_FILE = "acbr_output.json";

    @PostConstruct
    public void init() {
        logger.info("Scheduler initialized");
    }
    
    // cron format is: second minute hour day-of-month month day-of-week
    // This cron expression means: at 6:00 AM on the first day of every month
    @Scheduled(cron = "0 0 6 1 * ?") 
    public void runScheduler() {
        logger.info("Starting scheduler process...");

        List<Subscription> activeSubscriptions = retrieveActiveSubscriptions();
        if (activeSubscriptions.isEmpty()) {
            logger.info("No active subscriptions found.");
            return;
        }

        List<AppliedCustomerBillingRate> allAcbrList = retrieveAllACBR(activeSubscriptions);
        saveAppliedCustomerBillingRates(allAcbrList);
    }
    
    // TODO: MOVE TO BILLS SERVICE, CHECK WITH FEDERICO
    private List<Subscription> retrieveActiveSubscriptions() {
        try {
            List<Subscription> subscriptions = subscriptionService.getAllSubscriptions();
            List<Subscription> activeSubs = subscriptions.stream()
                    .filter(sub -> "active".equalsIgnoreCase(sub.getStatus()))
                    .toList();
            logger.info("Found {} active subscriptions", activeSubs.size());
            return activeSubs;
        } catch (Exception e) {
            logger.error("Failed to retrieve active subscriptions", e);
            return List.of();
        }
    }
    
    private List<AppliedCustomerBillingRate> retrieveAllACBR(List<Subscription> activeSubscriptions) {
        List<AppliedCustomerBillingRate> allAcbrList = new ArrayList<>();

        for (Subscription subscription : activeSubscriptions) {
            String subscriptionId = subscription.getId();
            logger.info("Processing subscription: {}", subscriptionId);

            List<SimpleBill> bills;
            try {
                bills = billsService.getSubscriptionBills(subscriptionId);
            } catch (Exception e) {
                logger.error("Failed to retrieve bills for subscription {}: {}", subscriptionId, e.getMessage(), e);
                continue;
            }

            if (bills.isEmpty()) {
                logger.warn("No bills found for subscription: {}", subscriptionId);
                continue;
            }

            for (SimpleBill bill : bills) {
                try {
                    List<AppliedCustomerBillingRate> acbrList = billsService.buildABCRList(bill);
                    allAcbrList.addAll(acbrList);
                } catch (Exception e) {
                    logger.error("Failed to generate ACBR list for bill {} (subscription {}): {}", bill.getId(), subscriptionId, e.getMessage(), e);
                }
            }

            logger.info("Completed processing for subscription: {}", subscriptionId);
        }

        return allAcbrList;
    }

    private void saveAppliedCustomerBillingRates(List<AppliedCustomerBillingRate> acbrList) {
        if (acbrList.isEmpty()) {
            logger.warn("No AppliedCustomerBillingRate entries to save.");
            return;
        }

        try {
            Path path = Paths.get(OUTPUT_FOLDER);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created directory: {}", path.toAbsolutePath());
            }

            File file = new File(path.toFile(), OUTPUT_FILE);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, acbrList);
            logger.info("Saved AppliedCustomerBillingRate list to file: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save AppliedCustomerBillingRate list to JSON file: {}", e.getMessage(), e);
        }
    }
}
