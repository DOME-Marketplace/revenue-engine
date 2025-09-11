package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Report;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.SimpleBill;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.service.cached.CachedStatementsService;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

import it.eng.dome.revenue.engine.service.cached.CacheService;
import it.eng.dome.revenue.engine.service.cached.CachedPlanService;


@Service
public class ReportingService {
    
    protected final Logger logger = LoggerFactory.getLogger(ReportingService.class);

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private CachedPlanService planService;
    
    @Autowired
    private MetricsRetriever metricsRetriever;

    @Autowired
    private CachedStatementsService statementsService;
    
    @Autowired
    private BillsService billsService;
    
    private final Cache<String, List<Report>> reportCache;
    
    public ReportingService(CacheService cacheService) {
        this.reportCache = cacheService.getOrCreateCache(
            "reportCache",
            String.class,
            (Class<List<Report>>)(Class<?>)List.class,
            Duration.ofHours(1)
        );
    }

    /**
     * Retrieves the complete dashboard report for a given organization, using cache to avoid repeated computations.
     *
     * @param relatedPartyId the organization ID
     * @return a list of Report sections for the dashboard
     * @throws ApiException if an API call fails
     * @throws IOException if a file or network access fails
     */
    public List<Report> getDashboardReport(String relatedPartyId) throws ApiException, IOException {
        logger.info("Reporting for dashboard, Organization ID = {}", relatedPartyId);

        // Try to get the report from cache first
        List<Report> cachedReport = reportCache.get(relatedPartyId);
        if (cachedReport != null) {
            logger.info("Returning cached dashboard report for organization {}", relatedPartyId);
            return cachedReport;
        }

        List<Report> report = new ArrayList<>();

        // My Subscription Plan
        Report subscriptionSection = getSubscriptionSection(relatedPartyId);
        report.add(subscriptionSection);

        // Billing History
        report.add(getBillingHistorySection(relatedPartyId));

        // Revenue section
        report.add(getRevenueSection(relatedPartyId));

        // Bill Previsioning section 
        report.add(getPrevisioningSection(relatedPartyId));

        // Referral Program Area (computed)
        report.add(getReferralSection(relatedPartyId));

        // Support (hardcoded)
        report.add(new Report("Support", Arrays.asList(
            new Report("Email", "support@dome-marketplace.org"),
            new Report("Help Center", "Visit Support Portal", "https://www.dome-helpcenter.org")
        )));

        // Store in cache before returning
        reportCache.put(relatedPartyId, report);

        return report;
    }


    /**
	 * Retrieves the subscription section for the given relatedPartyId.
	 * 
	 * @param relatedPartyId the ID of the related party
	 * @return a Report object containing subscription details
	 * @throws ApiException if there is an error retrieving data from the API
	 * @throws IOException if there is an error reading data from files
	 */
    
    public Report getSubscriptionSection(String relatedPartyId) throws ApiException, IOException {
        Subscription subscription = subscriptionService.getSubscriptionByRelatedPartyId(relatedPartyId);
        if (subscription == null) {
            return new Report("Subscription", "No active subscription found for this user.");
        }
        Plan plan = planService.getPlanById(subscription.getPlan().getId());
        String planName = plan.getName() != null ? plan.getName() : "Unknown Plan";
        String startDate = subscription.getStartDate() != null ? subscription.getStartDate().toString() : "Unknown Start Date";
        String renewalDate = subscription.getStartDate() != null ? subscription.getStartDate().plusYears(1).toString() : "Unknown Renewal Date";
        
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
    
    /**
	 * Retrieves the billing history section for the given relatedPartyId.
	 * 
	 * @param relatedPartyId the ID of the related party
	 * @return a Report object containing billing history details
	*/
    public Report getBillingHistorySection(String relatedPartyId) {
    	String subscriptionId;
        try {
            subscriptionId = subscriptionService.getSubscriptionIdByRelatedPartyId(relatedPartyId);
        } catch (Exception e) {
            logger.error("Failed to retrieve subscriptionId for relatedPartyId: {}", relatedPartyId, e);
            return new Report(
                "Bills Provisioning",
                List.of(new Report("Error", "Unable to retrieve subscription information"))
            );
        }
        // check null or empty subscriptionId
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            logger.warn("Subscription ID is null or not found for Organization with ID: {}", relatedPartyId);
            return new Report("Bills Provisioning", List.of(new Report("Error", "Invalid subscription ID")));
        }

        // retrieve Subscription
        Subscription subscription = subscriptionService.getSubscriptionByProductId(subscriptionId);
        
        if (subscription == null || subscription.getStartDate() == null) {
            logger.warn("Subscription not found or missing start date for ID: {}", subscriptionId);
            return new Report("Billing History", "No billing data available");
        }

        // retrieve all bills
        List<SimpleBill> allBills;
        try {
            allBills = billsService.getSubscriptionBills(subscriptionId);
        } catch (Exception e) {
            logger.error("Failed to retrieve bills for subscription with ID: {}", subscriptionId, e);
            return new Report("Billing History", "No billing data available");
        }
        
        // sort bills by end date descending (most recent first)
        allBills.sort(
            Comparator.comparing(
                b -> b.getPeriod() != null ? b.getPeriod().getEndDateTime() : OffsetDateTime.MIN,
                Comparator.reverseOrder()
            )
        );

        OffsetDateTime now = OffsetDateTime.now();
        List<Report> invoiceReports = new ArrayList<>();

        // iterate over all bills to filter and build the billing history
        for (SimpleBill bill : allBills) {
        	
        	// skip malformed or incomplete bills
            if (bill == null || bill.getPeriod() == null || bill.getPeriod().getEndDateTime() == null) {
            	logger.warn("Skipping bill with missing or invalid period: {}", bill);
            	continue;
            }

            OffsetDateTime billEnd = bill.getPeriod().getEndDateTime();
           
            // include only past bills: those that ended before 'now'
            if (!billEnd.isBefore(now)) continue; // skip non-past bills

            List<Report> details = new ArrayList<>();
            details.add(new Report("Status", "Paid")); // status is hardcoded as "Paid"

            // format billing period for readability
            String periodText = String.format(
                "%s - %s",
                bill.getPeriod().getStartDateTime() != null ? bill.getPeriod().getStartDateTime().toLocalDate() : "-",
                billEnd.toLocalDate()
            );
            details.add(new Report("Period", periodText));

            Double amount = bill.getAmount() != null ? bill.getAmount() : 0.0;
            details.add(new Report("Amount", String.format("%.2f EUR", amount)));

            String label = "Invoice - " +
                (bill.getPeriod().getStartDateTime() != null ? bill.getPeriod().getStartDateTime().toLocalDate() : "Unknown");

            invoiceReports.add(new Report(label, details));
        }

        if (invoiceReports.isEmpty()) {
            return new Report("Billing History", "No billing data available");
        }

        return new Report("Billing History", invoiceReports);
    }

    /**	Retrieves the revenue section for the given relatedPartyId.
	 * 
	 * @param relatedPartyId the ID of the related party
	 * @return a Report object containing revenue details
	 * @throws ApiException if there is an error retrieving data from the API
	 * @throws IOException if there is an error reading data from files
	*/
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
    
    /** 
     * This section calculates the estimated and confirmed future bills for the current month
     * @param relatedPartyId
     * @return
    */
    public Report getPrevisioningSection(String relatedPartyId) {
        String subscriptionId;
        try {
            subscriptionId = subscriptionService.getSubscriptionIdByRelatedPartyId(relatedPartyId);
        } catch (Exception e) {
            logger.error("Failed to retrieve subscriptionId for Organization with ID: {}", relatedPartyId, e);
            return new Report(
                "Bills Provisioning",
                List.of(new Report("Error", "Unable to retrieve subscription information"))
            );
        }
        // check null or empty subscriptionId
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            logger.warn("Subscription ID is null or not found for Organization with ID: {}", relatedPartyId);
            return new Report("Bills Provisioning", List.of(new Report("Error", "Invalid subscription ID")));
        }

        // fetch all bills associated with the subscription
        List<SimpleBill> allBills;
        try {
            allBills = billsService.getSubscriptionBills(subscriptionId);
        } catch (Exception e) {
            logger.error("Failed to retrieve bills for subscription with ID: {}", subscriptionId, e);
            return new Report("Bills Provisioning", List.of(new Report("Error", "Unable to retrieve bills")));
        }

        // define time boundaries for the current month
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);

        double futureEstimatedTotal = 0.0;
        double futureConfirmedTotal = 0.0;
        double currentEstimatedTotal = 0.0;

        for (SimpleBill bill : allBills) {
            if (bill == null || bill.getPeriod() == null || bill.getPeriod().getEndDateTime() == null) {
                logger.warn("Skipping malformed bill: {}", bill);
                continue;
            }

            OffsetDateTime start = bill.getPeriod().getStartDateTime();
            OffsetDateTime end = bill.getPeriod().getEndDateTime();
            boolean isEstimated = Boolean.TRUE.equals(bill.isEstimated());
            double amount = bill.getAmount() != null ? bill.getAmount() : 0.0;

            // bills ending within the current month (used for estimated current period)
            if (!end.isBefore(startOfMonth) && !end.isAfter(endOfMonth)) {
            	currentEstimatedTotal += amount;
            }

            // future bills: those starting strictly after 'now'
            if (start != null && start.isAfter(now)) {
                if (isEstimated) {
                    futureEstimatedTotal += amount;
                } else {
                    futureConfirmedTotal += amount;
                }
            }
        }

        // format totals using eur
        NumberFormat euroFormat = NumberFormat.getCurrencyInstance(Locale.ITALY);
        List<Report> items = new ArrayList<>();
        // add report where is applicable
        if (currentEstimatedTotal > 0) {
        	String periodLabel = String.format(
        	        "Estimated Bill Volume for Period %s - %s",
        	        startOfMonth.toLocalDate(),
        	        endOfMonth.toLocalDate()
        	    );
    	    items.add(new Report(periodLabel, euroFormat.format(currentEstimatedTotal)));
        }
        if (futureConfirmedTotal > 0) {
            items.add(new Report("Future Confirmed (Not Estimated) Bills Volume", euroFormat.format(futureConfirmedTotal)));
        }
        if (futureEstimatedTotal > 0) {
            items.add(new Report("Future Estimated Bills Volume", euroFormat.format(futureEstimatedTotal)));
        }
        // message if there are no relevant bills
        if (items.isEmpty()) {
            items.add(new Report("Info", "No future bills available."));
        }

        return new Report("Bills Provisioning", items);
    }
    
    /**
	 * Retrieves the referral section for the given relatedPartyId.
	 * 
	 * @param relatedPartyId the ID of the related party
	 * @return a Report object containing referral details
	*/
    public Report getReferralSection(String relatedPartyId) {        
    	Subscription subscription;
        try {
            subscription = subscriptionService.getSubscriptionByRelatedPartyId(relatedPartyId);
        } catch (IOException | ApiException e) {
            logger.error("Failed to retrieve subscription for related party {}: {}", relatedPartyId, e.getMessage(), e);
            return new Report("Referral Program Area", List.of(
                new Report("Referred Providers", "N/A"),
                new Report("Reward Earned", "N/A")
            ));
        }
        
        SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(subscription);
        TimePeriod subscriptionPeriod = timeHelper.getSubscriptionPeriodAt(subscription.getStartDate());
        
        Integer referralProviders;
        try {
            referralProviders = metricsRetriever.computeReferralsProvidersNumber(
                subscription.getBuyerId(),
                subscriptionPeriod
            );
        } catch (Exception e) {
            logger.error("Failed to compute referral providers for buyer {}: {}", subscription.getBuyerId(), e.getMessage(), e);
            referralProviders = 0;
        }

        List<RevenueStatement> statements;
        try {
            statements = getRevenueStatements(relatedPartyId);
        } catch (IOException | ApiException e) {
            logger.error("Failed to retrieve revenue statements for Organization with ID {}: {}", relatedPartyId, e.getMessage(), e);
            statements = Collections.emptyList();
        }
        
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
        
        if (item.getItems() != null) {
            for (RevenueItem subItem : item.getItems()) {
                String percentage = extractRevenueSharePercentage(subItem);
                if (!("N/A".equals(percentage))) {
                	return percentage;
                }
            }
        }
        
        return "N/A";
    }

    /**
	 * Retrieves revenue statements for the given relatedPartyId.
	 * 
	 * @param relatedPartyId the ID of the related party
	 * @return a list of RevenueStatement objects
	 * @throws ApiException if there is an error retrieving data from the API
	 * @throws IOException if there is an error reading data from files
	 */
    public List<RevenueStatement> getRevenueStatements(String relatedPartyId) throws ApiException, IOException {
    	logger.info("Call getRevenueStatements with relatedPartyId: {}", relatedPartyId);

        String subscriptionId = subscriptionService.getSubscriptionIdByRelatedPartyId(relatedPartyId);
        logger.debug("Retrieved subscriptionId: {}", subscriptionId);

        try {
            return statementsService.getStatementsForSubscription(subscriptionId);
        } catch (ApiException | IOException e) {
            // Propagate specific declared exceptions
            logger.error("Error retrieving statements for subscription with ID {}: {}", subscriptionId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exception in a runtime one or log and return empty if you want to fail gracefully
            logger.error("Unexpected error retrieving statements for subscription with ID {}: {}", subscriptionId, e.getMessage(), e);
            throw new RuntimeException("Unexpected error retrieving statements", e);
        }
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
}