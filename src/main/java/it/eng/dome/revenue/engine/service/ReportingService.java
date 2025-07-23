package it.eng.dome.revenue.engine.service;

import it.eng.dome.revenue.engine.model.*;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ReportingService {
	
	protected final Logger logger = LoggerFactory.getLogger(ReportingService.class);

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    PlanService planService;

    @Autowired
    PriceCalculator priceCalculator;

    // Generates a list of RevenueStatement objects, one per charge period
    public List<RevenueStatement> getRevenueStatements(String relatedPartyId) throws ApiException, IOException {
    	
    	logger.info("Call getRevenueStatements with relatedPartyId: {}", relatedPartyId);
    	
    	String subscriptionId = subscriptionService.getSubscriptionIdByRelatedPartyId(relatedPartyId);
    	logger.info("Get subscriptionId: {}", subscriptionId);
    	
        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
        logger.info("Subscription: {}", subscription);
        
        
        if (subscription == null || subscription.getPlan() == null) {
        	return List.of();
        }

        logger.info("Plan id: {}", subscription.getPlan().getId());
        Plan plan = planService.findPlanById(subscription.getPlan().getId());
        
        if (plan == null || plan.getPrice() == null) {
        	return List.of();
        }

        subscription.setPlan(plan);
        priceCalculator.setSubscription(subscription);

        SubscriptionTimeHelper helper = new SubscriptionTimeHelper(subscription);
        List<RevenueStatement> statements = new ArrayList<>();

        for (TimePeriod period : helper.getChargePeriodTimes()) {
            RevenueItem revenueItem = priceCalculator.compute(plan.getPrice(), period.getStartDateTime());
            if (revenueItem != null) {
                statements.add(new RevenueStatement(subscription, period, revenueItem));
            }
        }

        return statements;
    }

    // Prepares the dashboard report including revenue
    public List<Reporting> getDashboardReport(String relatedPartyId) throws ApiException, IOException {
    	
    	logger.info("Call getDashboardReport for relatedPartyId: {}", relatedPartyId);
    	
        List<Reporting> report = new ArrayList<>();

        // 1: My Subscription Plan (hardcoded)
        report.add(new Reporting("My Subscription Plan", null, null, Arrays.asList(
            new Reporting("Plan Type", "Advanced"),
            new Reporting("Subscribed on", "2025-06-01"),
            new Reporting("Renewal Due", "2026-06-01"),
            new Reporting("Discounts", "10% referral, 20% performance")
        )));

        // 2: Billing History (hardcoded)
        report.add(new Reporting("Billing History", null, null, Arrays.asList(
            new Reporting("Invoice INV-2025-001", null, null, Arrays.asList(
                new Reporting("Status", "Paid"),
                new Reporting("Issued On", "2025-01-15"),
                new Reporting("Amount", "EUR 5,000"),
                new Reporting("Download", "Download PDF", "https://billing.dome.org/invoices/INV-2025-001")
            )),
            new Reporting("Invoice INV-2025-002", null, null, Arrays.asList(
                new Reporting("Status", "Pending"),
                new Reporting("Issued On", "2025-06-15"),
                new Reporting("Amount", "EUR 5,000"),
                new Reporting("Download", "Download PDF", "https://billing.dome.org/invoices/INV-2025-002")
            ))
        )));

        // 3: Revenue section (computed)
        List<RevenueStatement> statements = getRevenueStatements(relatedPartyId);
        report.add(generateRevenueSection(statements));

        // 4: Referral Program (hardcoded)
        report.add(new Reporting("Referral Program", null, null, Arrays.asList(
            new Reporting("Referred Providers", "5"),
            new Reporting("Reward Earned", "EUR 500")
        )));

        // 5: Change Request (hardcoded)
        report.add(new Reporting("Plan Change Request", null, null, Arrays.asList(
            new Reporting("Status", "Pending Review"),
            new Reporting("Requested Changing", "Basic to Advanced")
        )));

        // 6: Support (hardcoded)
        report.add(new Reporting("Support", null, null, Arrays.asList(
            new Reporting("Email", "support@dome-marketplace.org"),
            new Reporting("Help Center", "Visit Support Portal", "https://www.dome-helpcenter.org")
        )));

        return report;
    }

    // Converts a list of RevenueStatement into a nested Reporting section
    public Reporting generateRevenueSection(List<RevenueStatement> statements) {
        if (statements == null || statements.isEmpty()) {
            return new Reporting("Revenue", " ", null);
        }

        List<Reporting> items = new ArrayList<>();

        for (RevenueStatement rs : statements) {
            RevenueItem root = rs.getRevenueItem();
            if (root == null) continue;

            String currency = root.getCurrency() != null ? root.getCurrency() + " " : "";
            TimePeriod period = rs.getPeriod();
            OffsetDateTime startDateTime = period.getStartDateTime();
			OffsetDateTime endDateTime = period.getEndDateTime();
			String periodLabel = String.format("%s to %s",
                startDateTime != null ? startDateTime.toLocalDate() : "?",
                endDateTime != null ? endDateTime.toLocalDate() : "?"
            );

            double periodTotal = root.getOverallValue();

            List<Reporting> children = new ArrayList<>();
            children.add(new Reporting("Total Revenue", currency + format(periodTotal)));
            for (RevenueItem item : root.getItems()) {
                children.add(flattenRevenueItemNested(item, currency));
            }

            items.add(new Reporting("Period " + periodLabel, null, null, children));
        }

        Reporting revenueSection = new Reporting("Revenue", null);
        revenueSection.setItems(items);
        return revenueSection;
    }


    // Recursively transforms a RevenueItem into a nested Reporting object
    private Reporting flattenRevenueItemNested(RevenueItem item, String currency) {
        String label = item.getName();
        String text = (item.getValue() != null) ? currency + format(item.getValue()) : null;
        Reporting reporting = new Reporting(label, text);

        if (!item.getItems().isEmpty()) {
            List<Reporting> children = new ArrayList<>();
            for (RevenueItem child : item.getItems()) {
                children.add(flattenRevenueItemNested(child, currency));
            }
            reporting.setItems(children);
        }

        return reporting;
    }

    private String format(Double value) {
        if (value == null) return "-";
        return String.format("%,.2f", value).replace(',', 'X').replace('.', ',').replace('X', '.');
    }
}
