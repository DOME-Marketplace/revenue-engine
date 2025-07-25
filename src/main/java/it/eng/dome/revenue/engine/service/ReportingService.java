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
            RevenueStatement statement = priceCalculator.compute(period);
            if(statement!=null)
                statements.add(statement);
        }

        return statements;
    }

    public List<Reporting> getDashboardReport(String relatedPartyId) throws ApiException, IOException {
        logger.info("Call getDashboardReport for relatedPartyId: {}", relatedPartyId);
        
        List<Reporting> report = new ArrayList<>();

        // 1: My Subscription Plan
        Reporting subscriptionSection = getSubscriptionSection(relatedPartyId);
        report.add(subscriptionSection);

        // 2: Billing History (hardcoded)
        report.add(new Reporting("Billing History", Arrays.asList(
            new Reporting("Invoice INV-2025-001", Arrays.asList(
                new Reporting("Status", "Paid"),
                new Reporting("Issued On", "2025-01-15"),
                new Reporting("Download", "Download PDF", "https://billing.dome.org/invoices/INV-2025-001")
            )),
            new Reporting("Invoice INV-2025-002", Arrays.asList(
                new Reporting("Status", "Pending"),
                new Reporting("Issued On", "2025-06-15"),
                new Reporting("Download", "Download PDF", "https://billing.dome.org/invoices/INV-2025-002")
            ))
        )));

        // 3: Revenue section (computed) 
        List<RevenueStatement> statements = getRevenueStatements(relatedPartyId);
        report.add(getTotalRevenueSection(statements));

        // 4: Referral Program (hardcoded)
        report.add(new Reporting("Referral Program Area", Arrays.asList(
            new Reporting("Referred Providers", "5"),
            new Reporting("Discount Earned", "10%")
        )));

//        // 5: Change Request (hardcoded)
//        report.add(new Reporting("Plan Change Request", Arrays.asList(
//            new Reporting("Status", "Pending Review"),
//            new Reporting("Requested Changing", "Basic to Advanced")
//        )));

        // 6: Support (hardcoded)
        report.add(new Reporting("Support",Arrays.asList(
            new Reporting("Email", "support@dome-marketplace.org"),
            new Reporting("Help Center", "Visit Support Portal", "https://www.dome-helpcenter.org")
        )));

        return report;
    }

    public Reporting getSubscriptionSection(String relatedPartyId) throws ApiException, IOException {
        Subscription subscription = subscriptionService.getSubscriptionByRelatedPartyId(relatedPartyId);
        if (subscription == null) {
            return new Reporting("Subscription", "No active subscription found for this user.");
        }

        String planName = subscription.getPlan() != null ? subscription.getPlan().getName() : "Unknown Plan";
        String startDate = subscription.getStartDate() != null ? subscription.getStartDate().toString() : "Unknown Start Date";
        String renewalDate = subscription.getStartDate() != null ? subscription.getStartDate().plusYears(1).toString() : "Unknown Renewal Date";

        return new Reporting("My Subscription Plan", Arrays.asList(
            new Reporting("Plan Name", planName),
            new Reporting("Start Date", startDate),
            new Reporting("Renewal Date", renewalDate),
            new Reporting("Discounts", "10% referral, 20% performance")
        ));
    }

    public Reporting getTotalRevenueSection(List<RevenueStatement> statements) {
        if (statements == null || statements.isEmpty()) {
            return new Reporting("Revenue Summary", "No revenue data available");
        }

        List<Reporting> totalRevenueItems = new ArrayList<>();

        for (RevenueStatement rs : statements) {
            // FIXME: (PF) now that items are an array, returing the first item (to let it compile)
            RevenueItem root = rs.getRevenueItems().get(0);
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
            
            String label = String.format("Total Revenue for %s", periodLabel);
            totalRevenueItems.add(new Reporting(label, currency + format(periodTotal)));
        }

        return new Reporting("Revenue Volume Monitoring", totalRevenueItems);
    }

    private String format(Double value) {
        if (value == null) return "-";
        return String.format("%,.2f", value).replace(',', 'X').replace('.', ',').replace('X', '.');
    }
}