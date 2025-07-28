package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Report;
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

    @Autowired
    StatementsService statementsService;
    
    public List<Report> getDashboardReport(String relatedPartyId) throws ApiException, IOException {
        logger.info("Call getDashboardReport for relatedPartyId: {}", relatedPartyId);
        
        List<Report> report = new ArrayList<>();

        // 1: My Subscription Plan
        Report subscriptionSection = getSubscriptionSection(relatedPartyId);
        report.add(subscriptionSection);

        // 2: Billing History (hardcoded)
        report.add(new Report("Billing History", Arrays.asList(
            new Report("Invoice INV-2025-001", Arrays.asList(
                new Report("Status", "Paid"),
                new Report("Issued On", "2025-01-15"),
                new Report("Download", "Download PDF", "https://billing.dome.org/invoices/INV-2025-001")
            )),
            new Report("Invoice INV-2025-002", Arrays.asList(
                new Report("Status", "Pending"),
                new Report("Issued On", "2025-06-15"),
                new Report("Download", "Download PDF", "https://billing.dome.org/invoices/INV-2025-002")
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
        report.add(new Report("Support",Arrays.asList(
            new Report("Email", "support@dome-marketplace.org"),
            new Report("Help Center", "Visit Support Portal", "https://www.dome-helpcenter.org")
        )));

        return report;
    }


    
    public Report getSubscriptionSection(String relatedPartyId) throws ApiException, IOException {
        Subscription subscription = subscriptionService.getSubscriptionByRelatedPartyId(relatedPartyId);
        if (subscription == null) {
            return new Report("Subscription", "No active subscription found for this user.");
        }

        String planName = subscription.getPlan() != null ? subscription.getPlan().getName() : "Unknown Plan";
        String startDate = subscription.getStartDate() != null ? subscription.getStartDate().toString() : "Unknown Start Date";
        String renewalDate = subscription.getStartDate() != null ? subscription.getStartDate().plusYears(1).toString() : "Unknown Renewal Date";
        
        Plan plan = planService.findPlanById(subscription.getPlan().getId());
        
        String agreementsText = Optional.ofNullable(plan.getAgreements())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull) 
                .map(agreement -> "" + agreement.trim())  
                .collect(Collectors.joining(". ")); 

        return new Report("My Subscription Plan", Arrays.asList(
            new Report("Plan Name", planName),
            new Report("Start Date", startDate),
            new Report("Renewal Date", renewalDate),
            new Report("Agreements and Discounts", agreementsText)  
        ));
    }
    
    public Report getReferralSection(String relatedPartyId) throws Exception {
        
        logger.info("Call getReferralSection for relatedPartyId: {}", relatedPartyId);
        
        Integer referralProviders = 0;
        
        Subscription subscription = subscriptionService.getSubscriptionByRelatedPartyId(relatedPartyId);
           SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(subscription);
           TimePeriod subscriptionPeriod = timeHelper.getSubscriptionPeriodAt(subscription.getStartDate());
           
           referralProviders = metricsRetriever.computeReferralsProvidersNumber(
               subscription.getBuyerId(), 
               subscriptionPeriod
           );
           
           
        return  new Report("Referral Program Area", Arrays.asList(
                   new Report("Referred Providers", referralProviders.toString()),
                   // TODO: discounts should be computed
                   new Report("Discount Earned", "10%")
               ));
       }
    
    public List<RevenueStatement> getRevenueStatements(String relatedPartyId) throws ApiException, IOException {
        logger.info("Call getRevenueStatements with relatedPartyId: {}", relatedPartyId);
                
        
            String subscriptionId = subscriptionService.getSubscriptionIdByRelatedPartyId(relatedPartyId);
            logger.info("Get subscriptionId: {}", subscriptionId);

            // prepare output
            List<RevenueStatement> statements = new ArrayList<>();

            try {
				statements = statementsService.getStatementsForSubscription(subscriptionId);
			} catch (Exception e) {
				e.printStackTrace();
			}
            
            return statements;
            
    }
    
    
    public Report getTotalRevenueSection(List<RevenueStatement> statements) {
        if (statements == null || statements.isEmpty()) {
            return new Report("Revenue Volume Monitoring", "No revenue data available");
        }

        LocalDate today = OffsetDateTime.now().toLocalDate();
        Report monthly = null;
        Report yearly = null;

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
            if (containsToday && (duration < 32 && duration >= 28)) {
                
                monthly = new Report(
                    String.format("Current Monthly Revenue: "),
                    currency + format(value)
                );
            } else if (startDate.getMonth() == today.getMonth()
                    && startDate.getYear() == today.getYear()
                    && duration >= 364 && duration <= 366) {
                
                yearly = new Report(
                    String.format("Yearly Total: "),
                    currency + format(value)
                );
            }
        }

        List<Report> items = new ArrayList<>();
        if (monthly != null) items.add(monthly);
        if (yearly != null) items.add(yearly);

        return new Report("Revenue Volume Monitoring", items);
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