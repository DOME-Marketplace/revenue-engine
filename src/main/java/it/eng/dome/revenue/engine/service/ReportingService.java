package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Reporting;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.service.compute.PriceCalculator;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class ReportingService {
    
    protected final Logger logger = LoggerFactory.getLogger(ReportingService.class);

    @Autowired
    SubscriptionService subscriptionService;

    @Autowired
    PlanService planService;

    @Autowired
    PriceCalculator priceCalculator;
    
    @Autowired
    MetricsRetriever metricsRetriever;

    public List<RevenueStatement> getRevenueStatements(String relatedPartyId) throws ApiException, IOException {
        logger.info("Call getRevenueStatements with relatedPartyId: {}", relatedPartyId);
                
        try {
            String subscriptionId = subscriptionService.getSubscriptionIdByRelatedPartyId(relatedPartyId);
            logger.info("Get subscriptionId: {}", subscriptionId);

            // prepare output
            List<RevenueStatement> statements = new ArrayList<>();

            // retrieve the subscription by id
            Subscription sub = subscriptionService.getSubscriptionById(subscriptionId);
            logger.info("Subscription: {}", sub);

            // retrive the plan for the subscription
            Plan plan = this.planService.findPlanById(sub.getPlan().getId());

            // add the full plan to the subscription
            sub.setPlan(plan);

            // configure the price calculator
            priceCalculator.setSubscription(sub);

            // build all statements
            SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(sub);
            for(TimePeriod tp : timeHelper.getChargePeriodTimes()) {
                RevenueStatement statement = priceCalculator.compute(tp);
                if(statement!=null) {
                    statement.clusterizeItems();
                    statements.add(statement);
                }
            }

            // replace the plan with a reference
            sub.setPlan(plan.buildRef());

            return statements;
        } catch (Exception e) {
           logger.error(e.getMessage(), e);
           throw(e);
        }
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

		// 4: Referral Program Area (computed)
		try {
			report.add(getReferralSection(relatedPartyId));
		} catch (Exception e) {
			e.printStackTrace();
		}

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
            // TODO: Discounts should be computed
            new Reporting("Discounts", "10% referral, 20% performance")
        ));
    }
    
    public Reporting getReferralSection(String relatedPartyId) throws Exception {
        
        logger.info("Call getReferralSection for relatedPartyId: {}", relatedPartyId);
        
        Integer referralProviders = 0;
        
        Subscription subscription = subscriptionService.getSubscriptionByRelatedPartyId(relatedPartyId);
           SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(subscription);
           TimePeriod subscriptionPeriod = timeHelper.getSubscriptionPeriodAt(subscription.getStartDate());
           
           referralProviders = metricsRetriever.computeReferralsProvidersNumber(
               subscription.getBuyerId(), 
               subscriptionPeriod
           );
           
           
        return  new Reporting("Referral Program Area", Arrays.asList(
                   new Reporting("Referred Providers", referralProviders.toString()),
                   // TODO: discounts should be computed
                   new Reporting("Discount Earned", "10%")
               ));
       }
    
    
    public Reporting getTotalRevenueSection(List<RevenueStatement> statements) {
        if (statements == null || statements.isEmpty()) {
            return new Reporting("Revenue Volume Monitoring", "No revenue data available");
        }

        LocalDate today = OffsetDateTime.now().toLocalDate();
        Reporting monthly = null;
        Reporting yearly = null;

        for (RevenueStatement rs : statements) {
            RevenueItem root = rs.getRevenueItems() != null && !rs.getRevenueItems().isEmpty()
                ? rs.getRevenueItems().get(0)
                : null;
            if (root == null) continue;

            TimePeriod period = rs.getPeriod();
            OffsetDateTime start = period.getStartDateTime();
            OffsetDateTime end = period.getEndDateTime();
            if (start == null || end == null) continue;

            LocalDate startDate = start.toLocalDate();
            LocalDate endDate = end.toLocalDate();
            long duration = ChronoUnit.DAYS.between(startDate, endDate);
            double value = root.getOverallValue();
            String currency = root.getCurrency() != null ? root.getCurrency() + " " : "";

            boolean containsToday = !today.isBefore(startDate) && today.isBefore(endDate);
            
            // Check if the period is within the current month or year
            // FIXME: this is a bit of a hack(ONLY FOR CURRENT MONTH AND YEAR), but it works for now
            if (containsToday && (duration < 32 && duration > 28)) {
                
                monthly = new Reporting(
                    String.format("Current Monthly Revenue: "),
                    currency + format(value)
                );
            } else if (startDate.getMonth() == today.getMonth()
                    && startDate.getYear() == today.getYear()
                    && duration >= 364 && duration <= 366) {
                
                yearly = new Reporting(
                    String.format("Yearly Total: "),
                    currency + format(value)
                );
            }
        }

        List<Reporting> items = new ArrayList<>();
        if (monthly != null) items.add(monthly);
        if (yearly != null) items.add(yearly);

        return new Reporting("Revenue Volume Monitoring", items);
    }

//    public Reporting getTotalRevenueSection(List<RevenueStatement> statements) {
//        if (statements == null || statements.isEmpty()) {
//            return new Reporting("Revenue Summary", "No revenue data available");
//        }
//
//        List<Reporting> totalRevenueItems = new ArrayList<>();
//
//        for (RevenueStatement rs : statements) {
//            // FIXME: (PF) now that items are an array, returing the first item (to let it compile)
//            RevenueItem root = rs.getRevenueItems().get(0);
//            if (root == null) continue;
//
//            String currency = root.getCurrency() != null ? root.getCurrency() + " " : "";
//            TimePeriod period = rs.getPeriod();
//            OffsetDateTime startDateTime = period.getStartDateTime();
//            OffsetDateTime endDateTime = period.getEndDateTime();
//            String periodLabel = String.format("%s to %s",
//                startDateTime != null ? startDateTime.toLocalDate() : "?",
//                endDateTime != null ? endDateTime.toLocalDate() : "?"
//            );
//
//            double periodTotal = root.getOverallValue();
//            
//            String label = String.format("Total Revenue for %s", periodLabel);
//            totalRevenueItems.add(new Reporting(label, currency + format(periodTotal)));
//        }
//
//        return new Reporting("Revenue Volume Monitoring", totalRevenueItems);
//    }

    private String format(Double value) {
        if (value == null) return "-";
        return String.format("%,.2f", value).replace(',', 'X').replace('.', ',').replace('X', '.');
    }
    
}