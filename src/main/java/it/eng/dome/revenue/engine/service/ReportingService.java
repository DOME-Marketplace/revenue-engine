package it.eng.dome.revenue.engine.service;

import it.eng.dome.revenue.engine.model.*;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ReportingService {

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    PlanService planService;

    @Autowired
    PriceCalculator priceCalculator;

    // Generates the RevenueStatement object using current date
    public RevenueStatement getRevenueStatement(String subscriptionId) throws ApiException, IOException {
        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
        if (subscription == null || subscription.getPlan() == null) return null;

        Plan plan = planService.findPlanById(subscription.getPlan().getId());
        if (plan == null || plan.getPrice() == null) return null;

        OffsetDateTime now = OffsetDateTime.now();

        priceCalculator.setSubscription(subscription);
        RevenueItem revenueItem = priceCalculator.compute(plan.getPrice(), now);

        TimePeriod period = new SubscriptionTimeHelper(subscription).getSubscriptionPeriodAt(now);
        return new RevenueStatement(subscription, period, revenueItem);
    }

    // Prepares the dashboard report including revenue and mock sections
    public List<Reporting> getDashboardReport(String subscriptionId) throws ApiException, IOException {
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

        // 3: Revenue (computed)
        RevenueStatement statement = getRevenueStatement(subscriptionId);
        report.add(generateRevenueSection(statement));

        // 4: Referral Program (hardcoded)
        report.add(new Reporting("Referral Program", null, null, Arrays.asList(
            new Reporting("Referred Providers", "5"),
            new Reporting("Reward Earned", "EUR 500")
        )));

        // 5: Support (hardcoded)
        report.add(new Reporting("Support", null, null, Arrays.asList(
            new Reporting("Email", "support@dome-marketplace.org"),
            new Reporting("Help Center", "Visit Support Portal", "https://www.dome-helpcenter.org")
        )));

        return report;
    }

    // Generates the revenue section as a nested reporting tree
    public Reporting generateRevenueSection(RevenueStatement revenueStatement) {
        if (revenueStatement == null || revenueStatement.getRevenueItem() == null) {
            return new Reporting("Revenue", " ", null);
        }

        RevenueItem root = revenueStatement.getRevenueItem();
        String currency = root.getCurrency() != null ? root.getCurrency() + " " : "";
        Double total = root.getOverallValue();

        Reporting revenueSection = new Reporting("Revenue", null);
        List<Reporting> children = new ArrayList<>();
        children.add(new Reporting("Total Revenue", currency + format(total)));

        for (RevenueItem item : root.getItems()) {
            children.add(flattenRevenueItemNested(item, currency));
        }

        revenueSection.setItems(children);
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
