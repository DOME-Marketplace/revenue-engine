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
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class ReportingService {
    
    protected final Logger logger = LoggerFactory.getLogger(ReportingService.class);
    
    @Autowired
    TmfDataRetriever tmfDataRetriever;

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
        try {
			report.add(getBillingHistorySection(relatedPartyId));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        // 3: Revenue section (computed) 
        report.add(getRevenueSection(relatedPartyId));

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
    
    public Report getBillingHistorySection(String relatedPartyId) throws Exception {
        TimePeriod tp = new TimePeriod();
        String subscriptionId = subscriptionService.getSubscriptionIdByRelatedPartyId(relatedPartyId);
        Subscription subscription = subscriptionService.getSubscriptionById(subscriptionId);
        tp.setStartDateTime(subscription.getStartDate());
        tp.setEndDateTime(OffsetDateTime.now());
		
        List<AppliedCustomerBillingRate> acbrList = tmfDataRetriever.retrieveBills(relatedPartyId, tp, null);

        if (acbrList.isEmpty()) {
            return new Report("Billing History", "No billing data available");
        }

        List<Report> invoiceReports = new ArrayList<>();
        for (AppliedCustomerBillingRate acbr : acbrList) {
            List<Report> details = new ArrayList<>();
            boolean isPaid = Boolean.TRUE.equals(acbr.getIsBilled()) && acbr.getBill() != null;
            details.add(new Report("Status", isPaid ? "Paid" : "Pending"));
            details.add(new Report("Issued On", acbr.getDate() != null ? acbr.getDate().toString() : "-"));
//            if (acbr.getHref() != null) {
//                String link = "https://billing.dome.org/acbr/" + acbr.getHref(); // oppure dove ospiti il PDF
//                details.add(new Report("Download", "Download PDF", link));
//            }
            if(acbr.getBill() != null) {
            	invoiceReports.add(new Report("Invoice " + acbr.getBill().getId(), details));
            }
            else {
            	invoiceReports.add(new Report("ACBR " + acbr.getId(), details));
            }
        }

        return new Report("Billing History", invoiceReports);
    }

    public Report getRevenueSection(String relatedPartyId) throws ApiException, IOException {
    	List<RevenueStatement> statements = getRevenueStatements(relatedPartyId);
    	
        if (statements == null || statements.isEmpty()) {
            return new Report("Revenue Volume Monitoring", "No revenue data available");
        }

        LocalDate today = OffsetDateTime.now().toLocalDate();
        Report monthly = null;
        Report yearly = null;
        Report tier = null;

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
            String revenueSharePercentage = extractRevenueSharePercentage(root);

            // Check if the period is within the current month or year
            // FIXME: this is a bit of a hack(ONLY FOR CURRENT MONTH AND YEAR), but it works for now
            if (containsToday && (duration < 32 && duration >= 28)) {
                
                monthly = new Report("Current Monthly Revenue: ", currency + format(value));
                tier = new Report("Current Tier: ", revenueSharePercentage + " commission");

                
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
        if (tier != null) items.add(tier);
        if (yearly != null) items.add(yearly);
        

        return new Report("Revenue Volume Monitoring", items);
    }

    public Report getReferralSection(String relatedPartyId) throws Exception {        
        Subscription subscription = subscriptionService.getSubscriptionByRelatedPartyId(relatedPartyId);
        SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(subscription);
        TimePeriod subscriptionPeriod = timeHelper.getSubscriptionPeriodAt(subscription.getStartDate());
        
        Integer referralProviders = metricsRetriever.computeReferralsProvidersNumber(
            subscription.getBuyerId(), 
            subscriptionPeriod
        );

        List<RevenueStatement> statements = getRevenueStatements(relatedPartyId);
        
        List<RevenueItem> referralDiscounts = extractReferralDiscounts(statements);
        
        String discountEarned = calculateTotalDiscountEarned(referralDiscounts);
        
        return new Report("Referral Program Area", Arrays.asList(
            new Report("Referred Providers", referralProviders != null ? referralProviders.toString() : "0"),
            new Report("Reward Earned", discountEarned)));
    }

    private List<RevenueItem> extractReferralDiscounts(List<RevenueStatement> statements) {
        List<RevenueItem> discounts = new ArrayList<>();
        
        if (statements == null) return discounts;

        for (RevenueStatement statement : statements) {
            for (RevenueItem item : statement.getRevenueItems()) {
                // Search recursively through all nested items
                findNestedReferralDiscounts(item, discounts);
            }
        }
        return discounts;
    }

    private void findNestedReferralDiscounts(RevenueItem item, List<RevenueItem> discounts) {
        if (item == null) return;
        
        // Check if current item is a referral discount
        if (isReferralDiscount(item)) {
            discounts.add(item);
        }
        
        // Recursively check nested items
        if (item.getItems() != null) {
            for (RevenueItem subItem : item.getItems()) {
                findNestedReferralDiscounts(subItem, discounts);
            }
        }
    }

    private boolean isReferralDiscount(RevenueItem item) {
        return item != null && 
               item.getName() != null && 
               (item.getName().toLowerCase().contains("referr") || 
                item.getName().toLowerCase().contains("discount")) &&
               item.getValue() != null && 
               item.getValue() < 0; // Only negative values (actual discounts)
    }

    private String calculateTotalDiscountEarned(List<RevenueItem> discountItems) {
        double total = discountItems.stream()
            .filter(Objects::nonNull)
            .mapToDouble(item -> Math.abs(item.getValue()))
            .sum();
        
        return format(total) + " EUR";
    }

    private String extractRevenueSharePercentage(RevenueItem item) {
        if (item == null) return "N/A";
        
        if (item.getName() != null && item.getName().contains("% revenue share")) {
            return item.getName().split("%")[0] + "%";
        }
        
        // Cerca negli items annidati
        if (item.getItems() != null) {
            for (RevenueItem subItem : item.getItems()) {
                String percentage = extractRevenueSharePercentage(subItem);
                if (!percentage.equals("N/A")) {
                    return percentage;
                }
            }
        }
        
        return "N/A";
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

    private String format(Double value) {
        if (value == null) return "-";
        return String.format("%,.2f", value);
    }
    
//  public Reporting getTotalRevenueSection(List<RevenueStatement> statements) {
//      if (statements == null || statements.isEmpty()) {
//          return new Reporting("Revenue Summary", "No revenue data available");
//      }
//
//      List<Reporting> totalRevenueItems = new ArrayList<>();
//
//      for (RevenueStatement rs : statements) {
//          // FIXME: (PF) now that items are an array, returing the first item (to let it compile)
//          RevenueItem root = rs.getRevenueItems().get(0);
//          if (root == null) continue;
//
//          String currency = root.getCurrency() != null ? root.getCurrency() + " " : "";
//          TimePeriod period = rs.getPeriod();
//          OffsetDateTime startDateTime = period.getStartDateTime();
//          OffsetDateTime endDateTime = period.getEndDateTime();
//          String periodLabel = String.format("%s to %s",
//              startDateTime != null ? startDateTime.toLocalDate() : "?",
//              endDateTime != null ? endDateTime.toLocalDate() : "?"
//          );
//
//          double periodTotal = root.getOverallValue();
//          
//          String label = String.format("Total Revenue for %s", periodLabel);
//          totalRevenueItems.add(new Reporting(label, currency + format(periodTotal)));
//      }
//
//      return new Reporting("Revenue Volume Monitoring", totalRevenueItems);
//  }
  
//  public Report getBillingHistorySection(String relatedPartyId) throws Exception {
//      String subscriptionId = subscriptionService.getSubscriptionIdByRelatedPartyId(relatedPartyId);
//      List<RevenueStatement> statements = statementsService.getStatementsForSubscription(subscriptionId);
//      
//		List<AppliedCustomerBillingRate> acbrList = new ArrayList<>();
//		for (RevenueStatement statement : statements) {
//		    AppliedCustomerBillingRate acbr = revenueService.buildACBR(statement);
//		    if (acbr != null) {
//		        acbrList.add(acbr);
//		    }
//		}
//
//      if (acbrList.isEmpty()) {
//          return new Report("Billing History", "No billing data available");
//      }
//
//      List<Report> invoiceReports = new ArrayList<>();
//      for (AppliedCustomerBillingRate acbr : acbrList) {
//          List<Report> details = new ArrayList<>();
//          boolean isPaid = Boolean.TRUE.equals(acbr.getIsBilled()) && acbr.getBill() != null;
//          details.add(new Report("Status", isPaid ? "Paid" : "Pending"));
//          details.add(new Report("Issued On", acbr.getDate() != null ? acbr.getDate().toString() : "-"));
////          if (acbr.getHref() != null) {
////              String link = "https://billing.dome.org/acbr/" + acbr.getHref(); // oppure dove ospiti il PDF
////              details.add(new Report("Download", "Download PDF", link));
////          }
//          invoiceReports.add(new Report("ACBR " + acbr.getId(), details));
//      }
//
//      return new Report("Billing History", invoiceReports);
//  }
  
}