package it.eng.dome.revenue.engine.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.exception.BadRevenuePlanException;
import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.exception.NotFoundException;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Report;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.service.cached.CachedPlanService;
import it.eng.dome.revenue.engine.service.cached.CachedStatementsService;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.utils.RelatedPartyUtils;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.Characteristic;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;


// FIXME: ACTIVE SUBSCRIPTIONS ONLY FOR NOW
@Service
public class ReportingService implements InitializingBean {

    protected final Logger logger = LoggerFactory.getLogger(ReportingService.class);
    
    //FIXME: Currency should be dynamic
    private final static String EUR_CURRENCY = "EUR";

    @Autowired
    private CachedSubscriptionService subscriptionService;

    @Autowired
    private CachedPlanService planService;

    @Autowired
    private CachedStatementsService statementsService;

    @Autowired
    private TmfCachedDataRetriever tmfDataRetriever;

    public ReportingService() {}

    public void afterPropertiesSet() {}

	public List<Report> totalSubscriptionRevenueSection() {
	    try {
	        List<Subscription> subscriptions = subscriptionService.getAllSubscriptions();
	        if (subscriptions == null || subscriptions.isEmpty()) {
	            return List.of(new Report("Total Subscription Revenue", "No active subscriptions found"));
	        }
	
	        List<Report> cloudProviders = new ArrayList<>();
	        List<Report> federatedProviders = new ArrayList<>();
	        double totalCloud = 0.0;
	        double totalFederated = 0.0;
	
	        LocalDate today = LocalDate.now();
	        LocalDate periodStart = today.withDayOfYear(1);
	        LocalDate periodEnd = today.withDayOfMonth(today.lengthOfMonth());
	
	        for (Subscription sub : subscriptions) {
	            try {
	                List<RevenueItem> items = statementsService.getItemsForSubscription(sub.getId());
	                if (items == null || items.isEmpty()) continue;
	
	                double yearlyTotal = items.stream()
	                        .filter(ri -> {
	                            LocalDate chargeDate = ri.getChargeTime().toLocalDate();
	                            return !chargeDate.isBefore(periodStart) && !chargeDate.isAfter(periodEnd);
	                        })
	                        .mapToDouble(RevenueItem::getOverallValue)
	                        .sum();
	
	                Product product = tmfDataRetriever.getProduct(sub.getId(), null);
	                String buyerId = product.getRelatedParty().stream()
	                        .filter(rp -> Role.BUYER.getValue().equalsIgnoreCase(rp.getRole()))
	                        .map(rp -> rp.getId())
	                        .findFirst().orElse("Unknown Subscriber");
	                String name = tmfDataRetriever.getOrganization(buyerId).getTradingName();
	
	                if (isFederated(product)) {
	                    federatedProviders.add(new Report(name, EUR_CURRENCY + format(yearlyTotal)));
	                    totalFederated += yearlyTotal;
	                } else {
	                    cloudProviders.add(new Report(name, EUR_CURRENCY + format(yearlyTotal)));
	                    totalCloud += yearlyTotal;
	                }
	
	            } catch (Exception e) {
	                logger.warn("Skipping subscription {} due to error: {}", sub.getId(), e.getMessage());
	            }
	        }
	
	        cloudProviders.sort((r1, r2) -> Double.compare(parseCurrency(r2.getText()), parseCurrency(r1.getText())));
	        federatedProviders.sort((r1, r2) -> Double.compare(parseCurrency(r2.getText()), parseCurrency(r1.getText())));
	
	        List<Report> result = new ArrayList<>();
	
	        if (!cloudProviders.isEmpty()) {
	            result.add(new Report(
	                    "Total Cloud Service Providers (" + periodStart + " - " + periodEnd + "): " 
	                    + EUR_CURRENCY + format(totalCloud),
	                    cloudProviders
	            ));
	
	            List<Report> topCloud = cloudProviders.stream().limit(2).collect(Collectors.toList());
	            result.add(new Report(
	                    "Top Cloud Service Providers (" + periodStart + " - " + periodEnd + "): ",
	                    topCloud
	            ));
	        }
	
	        if (!federatedProviders.isEmpty()) {
	            result.add(new Report(
	                    "Total Federated Marketplaces (" + periodStart + " - " + periodEnd + "): " 
	                    + EUR_CURRENCY + format(totalFederated),
	                    federatedProviders
	            ));
	
	            List<Report> topFederated = federatedProviders.stream().limit(2).collect(Collectors.toList());
	            result.add(new Report(
	                    "Top Federated Marketplaces (" + periodStart + " - " + periodEnd + "): ",
	                    topFederated
	            ));
	        }
	
	        Double totalOverall = totalCloud + totalFederated;
	        result.add(new Report(
	                "Overall Total Revenue (" + periodStart + " - " + periodEnd + "):",
	                EUR_CURRENCY + format(totalOverall)
	        ));
	
	        return result;
	    } catch (BadTmfDataException | ExternalServiceException e) {
	        logger.error("totalSubscriptionRevenueSection failed", e);
	        return List.of(new Report("Total Subscription Revenue", "No data available: " + e.getMessage()));
	    } catch (Exception e) {
	        logger.error("Unexpected error in totalSubscriptionRevenueSection", e);
	        return List.of(new Report("Total Subscription Revenue", "Error retrieving total subscription revenue"));
	    }
	}


    public List<Report> buildTopAndTotalBoxes(Report totalRevenueReport) {
        try {
            if (totalRevenueReport.getItems() == null || totalRevenueReport.getItems().isEmpty()) {
                return List.of(new Report("No data", "No revenue data available"));
            }

            List<Report> singleProviders = new ArrayList<>();
            List<Report> federatedProviders = new ArrayList<>();
            List<Report> totalSingle = new ArrayList<>();
            List<Report> totalFederated = new ArrayList<>();

            for (Report section : totalRevenueReport.getItems()) {
                if (section.getItems() == null || section.getItems().isEmpty()) continue;
                for (Report r : section.getItems()) {
                    String label = r.getLabel().toLowerCase();
                    if ("cloud service providers".equalsIgnoreCase(section.getLabel())) {
                        if (label.contains("total")) totalSingle.add(r);
                        else singleProviders.add(r);
                    } else if ("federated marketplaces".equalsIgnoreCase(section.getLabel())) {
                        if (label.contains("total")) totalFederated.add(r);
                        else federatedProviders.add(r);
                    }
                }
            }

            List<Report> topSingle = singleProviders.stream()
                    .sorted((r1, r2) -> Double.compare(parseCurrency(r2.getText()), parseCurrency(r1.getText())))
                    .limit(2).collect(Collectors.toList());

            List<Report> topFederated = federatedProviders.stream()
                    .sorted((r1, r2) -> Double.compare(parseCurrency(r2.getText()), parseCurrency(r1.getText())))
                    .limit(2).collect(Collectors.toList());

            List<Report> result = new ArrayList<>();

            result.add(new Report("Total Cloud Service Providers ", totalSingle));
            result.add(new Report("Total Federated Marketplaces", totalFederated));
            result.add(new Report("Top Cloud Service Providers", topSingle));
            result.add(new Report("Top Federated Marketplaces", topFederated));

            return result;
        } catch (Exception e) {
            logger.error("buildTopAndTotalBoxes failed", e);
            return List.of(new Report("Top and Total Boxes", "Error building top and total boxes"));
        }
    }

    public Report membersSection() {
        try {

            List<Product> singleProviders = new ArrayList<>();
            List<Product> federatedProviders = new ArrayList<>();

            tmfDataRetriever.fetchActiveProducts(50,
                product -> {
                    if (isFederated(product)) {
                        federatedProviders.add(product);
                    } else {
                        singleProviders.add(product);
                    }
            });

            int nrSingle = singleProviders.size();
            int nrFederated = federatedProviders.size();

            Map<String, Integer> activeSellersPerFederated = new HashMap<>();
            for (Product p : federatedProviders) {
                String marketplaceId = getMarketplaceId(p);
                if (marketplaceId == null) continue;
                if (!activeSellersPerFederated.containsKey(marketplaceId)) {
                    OffsetDateTime start = subscriptionService.getActiveSubscriptionByRelatedPartyId(marketplaceId).getStartDate();
                    OffsetDateTime end = OffsetDateTime.now();
                    TimePeriod tp = new TimePeriod();
                    tp.setStartDateTime(start);
                    tp.setEndDateTime(end);

                    int nrSellers = tmfDataRetriever.listActiveSellersBehindFederatedMarketplace(marketplaceId, tp).size();
                    Organization org = tmfDataRetriever.getOrganization(marketplaceId);
                    String orgName = org != null ? org.getTradingName() : "Unknown";
                    activeSellersPerFederated.put(orgName, nrSellers);
                }
            }

            List<Report> items = new ArrayList<>();
            items.add(new Report("Cloud Service Providers", String.valueOf(nrSingle)));
            items.add(new Report("Federated Marketplaces", String.valueOf(nrFederated)));
            for (Map.Entry<String, Integer> entry : activeSellersPerFederated.entrySet()) {
                items.add(new Report("Marketplace " + entry.getKey() + " - Active Sub-Sellers", String.valueOf(entry.getValue())));
            }

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime startOfYear = now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

            return new Report("Members Summary (" + startOfYear.toLocalDate() + " - " + now.toLocalDate()+") ", items);
        } catch (BadTmfDataException | ExternalServiceException e) {
            logger.error("membersSection failed", e);
            return new Report("Members Summary", "No data available: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in membersSection", e);
            return new Report("Members Summary", "Error retrieving members summary");
        }
    }

    public List<Report> getDashboardReport(String relatedPartyId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {

            if (relatedPartyId == null || relatedPartyId.isEmpty()) {
                throw new BadTmfDataException("Organization", relatedPartyId, "Related Party ID cannot be null or empty");
            }
            if (tmfDataRetriever.getOrganization(relatedPartyId) == null) {
                throw new NotFoundException("Organization not found: " + relatedPartyId);
            }

            // Flag to track if the related party is a Dome Operator
            AtomicBoolean isDomeOp = new AtomicBoolean(false);
            // Local stop flag for short-circuiting this specific batch iteration
            AtomicBoolean stop = new AtomicBoolean(false);

            tmfDataRetriever.fetchActiveProducts(50, product -> {
                if (stop.get()) return; // stop consuming further products for this check

                if (RelatedPartyUtils.productHasPartyWithRole(product, relatedPartyId, Role.SELLER_OPERATOR)) {
                    isDomeOp.set(true);
                    stop.set(true); // short-circuit locally
                }
            });

            if (isDomeOp.get()) {
                return handleDomeOperator();
            } else {
                return handleProvider(relatedPartyId);
            }
    }

    private List<Report> handleProvider(String relatedPartyId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        List<Report> report = new ArrayList<>();

        Report subscriptionReport = getSubscriptionSection(relatedPartyId);
        report.add(subscriptionReport);
        report.add(getBillingHistorySection(relatedPartyId));
        if (Boolean.TRUE.equals(subscriptionReport.isNoActiveSubscription())) {
            return report;
        }
        report.add(getRevenueSection(relatedPartyId));

        return report;
    }

    private List<Report> handleDomeOperator() {
        List<Report> report = new ArrayList<>();

        List<Report> revenueReports = totalSubscriptionRevenueSection();
        report.addAll(revenueReports);
        report.add(membersSection());

        return report;
    }

    public Report getSubscriptionSection(String relatedPartyId) {
        try {
            Subscription subscription = subscriptionService.getActiveSubscriptionByRelatedPartyId(relatedPartyId);

            if (subscription == null) {
                return new Report("Subscription", "You have no subscription as of today, "
                        + OffsetDateTime.now().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".", true);
            }

            Plan plan = planService.getPlanById(subscription.getPlan().getId());
            String planName = plan.getName() != null ? plan.getName() : "Unknown Plan";
            SubscriptionTimeHelper th = new SubscriptionTimeHelper(subscription);

            String startDate = th.getSubscriptionPeriodAt(OffsetDateTime.now()).getStartDateTime().toLocalDate().toString();
            String renewalDate = th.getSubscriptionPeriodAt(OffsetDateTime.now()).getEndDateTime().toLocalDate().toString();


            return new Report("My Subscription Plan", Arrays.asList(
                    new Report("Plan Name", planName),
                    new Report("Start Date", startDate),
                    new Report("Renewal Date", renewalDate)
            ));
        } catch (BadTmfDataException | BadRevenuePlanException | ExternalServiceException e) {
            logger.error("getSubscriptionSection failed", e);
            return new Report("My Subscription Plan", "No data available: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getSubscriptionSection", e);
            return new Report("My Subscription Plan", "Error retrieving subscription information");
        }
    }

    public Report getBillingHistorySection(String relatedPartyId) {
    	
    	TimePeriod period = new TimePeriod();
        try {
            List<CustomerBill> allBills = tmfDataRetriever.retrieveCustomerBills(
                    relatedPartyId,
                    Role.BUYER,
                    period
            );

            // FIXME: Temporary filter to include only bills created by the revenue engine, how to avoid this?
            allBills = allBills.parallelStream()
                    .filter(cb -> cb.getCategory() != null && "created by the revenue engine".equalsIgnoreCase(cb.getCategory()))
                    .collect(Collectors.toList());

            if (allBills == null || allBills.isEmpty()) {
                return new Report("Billing History", "No billing data available");
            }

            allBills.sort(Comparator.comparing(CustomerBill::getBillDate).reversed());

            List<Report> invoices = new ArrayList<>();
            for (CustomerBill cb : allBills) {
                String date = cb.getBillDate() != null ? cb.getBillDate().toLocalDate().toString() : "Unknown Date";
                List<Report> details = new ArrayList<>();

                if (cb.getTaxIncludedAmount() != null) {
                    Float amount = cb.getTaxIncludedAmount().getValue();
                    String unit = cb.getTaxIncludedAmount().getUnit() != null ? cb.getTaxIncludedAmount().getUnit() : "";
                    details.add(new Report("Amount", String.format("%.2f %s", amount, unit)));
                }

                if (cb.getState() != null) {
                    details.add(new Report("Status", cb.getState().getValue()));
                } else {
                    details.add(new Report("Status", "Not Available"));
                }

                invoices.add(new Report("Invoice - " + date, details));

            }

            return new Report("Billing History", invoices);

        } catch (Exception e) {
            logger.error("Error building billing history section", e);
            return new Report("Billing History", "Error retrieving billing history");
        }
    }

    public Report getRevenueSection(String relatedPartyId) {
        try {
            LocalDate today = LocalDate.now();
            
            // Current period: from one month ago to today
            LocalDate currentPeriodStart = today.minusMonths(1);
            LocalDate currentPeriodEnd = today;

            // Get subscription to determine the yearly period
            Subscription subscription = subscriptionService.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
            
            // Calculate subscription year start (anniversary-based)
            LocalDate subscriptionYearStart = calculateSubscriptionYearStart(subscription, today);

            List<CustomerBill> bills = tmfDataRetriever.retrieveCustomerBills(
                    relatedPartyId,
                    Role.BUYER,
                    new TimePeriod()
            );
                      
            // Filter only bills created by the revenue engine
            bills = bills.parallelStream()
                    .filter(cb -> cb.getCategory() != null && 
                            cb.getCategory().toLowerCase().contains("created by the revenue engine"))
                    .collect(Collectors.toList());
            
            if (bills == null || bills.isEmpty()) {
                return new Report("Revenue Summary", "No billing data available");
            }

            // Calculate yearly total (from subscription year start to today)
            double yearlyTotal = bills.parallelStream()
                    .filter(cb -> cb.getBillDate() != null &&
                                  !cb.getBillDate().toLocalDate().isBefore(subscriptionYearStart) &&
                                  !cb.getBillDate().toLocalDate().isAfter(today))
                    .filter(cb -> cb.getTaxIncludedAmount() != null && cb.getTaxIncludedAmount().getValue() != null)
                    .mapToDouble(cb -> cb.getTaxIncludedAmount().getValue())
                    .sum();

            // Calculate current period revenue (from one month ago to today)
            double currentPeriodRevenue = bills.parallelStream()
                    .filter(cb -> cb.getBillDate() != null &&
                                  !cb.getBillDate().toLocalDate().isBefore(currentPeriodStart) &&
                                  !cb.getBillDate().toLocalDate().isAfter(currentPeriodEnd))
                    .filter(cb -> cb.getTaxIncludedAmount() != null && cb.getTaxIncludedAmount().getValue() != null)
                    .mapToDouble(cb -> cb.getTaxIncludedAmount().getValue())
                    .sum();

            // Get current tier from statements
            String currentTier = getCurrentTierFromStatements(relatedPartyId, currentPeriodStart, currentPeriodEnd);

            List<Report> details = List.of(
                    new Report("Current Period Revenue (" + currentPeriodStart + " - " + currentPeriodEnd + ")",
                            "EUR " + format(currentPeriodRevenue)),
                    new Report("Yearly Total (" + subscriptionYearStart + " - " + today + ")", 
                            "EUR " + format(yearlyTotal)),
                    new Report("Current Tier", currentTier)
            );

            return new Report("Revenue Summary", details);

        } catch (Exception e) {
            logger.error("Error building revenue section", e);
            return new Report("Revenue Summary", "Error retrieving revenue data");
        }
    }

    /**
     * Get current tier from statements based on the period range.
     * Looks for statements whose period overlaps with the given range.
     */
    private String getCurrentTierFromStatements(String relatedPartyId, LocalDate periodStart, LocalDate periodEnd) {
        try {
            Subscription subscription = subscriptionService.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
            if (subscription == null || subscription.getId() == null) {
                return "No applicable tier";
            }

            List<RevenueItem> items = statementsService.getItemsForSubscription(subscription.getId());
            if (items == null || items.isEmpty()) {
                return "No applicable tier";
            }

            // Find statements that overlap with the current period
            for (RevenueItem ri : items) {
                if (ri.getPeriod() == null) continue;
                
                OffsetDateTime stmtStart = ri.getPeriod().getStartDateTime();
                OffsetDateTime stmtEnd = ri.getPeriod().getEndDateTime();
                
                if (stmtStart == null || stmtEnd == null) continue;
                
                LocalDate stmtStartDate = stmtStart.toLocalDate();
                LocalDate stmtEndDate = stmtEnd.toLocalDate();
                
                // Check if statement period overlaps with current period
                boolean overlaps = !stmtEndDate.isBefore(periodStart) && !stmtStartDate.isAfter(periodEnd);
                
                if (overlaps) {
                    // Found overlapping period - check if it has revenue > 0
                    if (ri.getOverallValue() <= 0) {
                        continue; // Check next statement
                    }
                    return extractCurrentTier(ri);
                }
            }

            return "No applicable tier";

        } catch (Exception e) {
            logger.error("Error getting current tier from statements", e);
            return "No applicable tier";
        }
    }

	/**
	 * Calculate the start of the current subscription year.
	 * The subscription year is based on the subscription anniversary date, not the calendar year.
	 * 
	 * Example: If subscription started on 2025-02-11 and today is 2026-02-03,
	 * the current subscription year started on 2025-02-11.
	 * 
	 * @param subscription the subscription
	 * @param today current date
	 * @return the start date of the current subscription year
	 */
	private LocalDate calculateSubscriptionYearStart(Subscription subscription, LocalDate today) {
	    if (subscription == null || subscription.getStartDate() == null) {
	        // Fallback to calendar year if no subscription
	        return today.withDayOfYear(1);
	    }
	    
	    LocalDate subscriptionStart = subscription.getStartDate().toLocalDate();
	    
	    // Find the most recent anniversary date that is not in the future
	    LocalDate anniversaryThisYear = subscriptionStart.withYear(today.getYear());
	    
	    if (anniversaryThisYear.isAfter(today)) {
	        // Anniversary hasn't occurred this year yet, use last year's anniversary
	        return anniversaryThisYear.minusYears(1);
	    } else {
	        // Anniversary has occurred this year or is today
	        return anniversaryThisYear;
	    }
	}

    /**
     * Extract the current tier from a RevenueItem.
     * Looks for items with percentage in name and positive value.
     * If overall value is 0, returns "No applicable tier".
     */
    private String extractCurrentTier(RevenueItem item) {
        if (item == null) return "No applicable tier";
        
        // If overall value is 0 or negative, no tier applies
        if (item.getOverallValue() <= 0) {
            return "No applicable tier";
        }
        
        // Find the highest tier (last one with value > 0 in the hierarchy)
        String tier = findHighestApplicableTier(item);
        
        return tier != null ? tier : "No applicable tier";
    }

    /**
     * Recursively find the highest applicable tier percentage from the item tree.
     * Returns the percentage from items that have positive values and contain percentage in name.
     */
    private String findHighestApplicableTier(RevenueItem item) {
        if (item == null) return null;
        
        String foundTier = null;
        
        // Check nested items first (they contain the actual tier breakdown)
        if (item.getItems() != null && !item.getItems().isEmpty()) {
            for (RevenueItem sub : item.getItems()) {
                String subTier = findHighestApplicableTier(sub);
                if (subTier != null) {
                    foundTier = subTier; // Keep the last (highest) tier found
                }
            }
        }
        
        // Check this item if it has a percentage and positive value
        // Looking for patterns like "3% revenue sharing" or "2.5% revenue sharing"
        if (item.getOverallValue() > 0 && item.getName() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+(?:\\.\\d+)?)%\\s*revenue\\s*sharing", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(item.getName());
            if (m.find()) {
                foundTier = m.group(1) + "%";
            }
        }
        
        return foundTier;
    }


    private boolean isFederated(Product p) {
        if (p.getProductCharacteristic() == null) return false;
        for (Characteristic ch : p.getProductCharacteristic()) {
            if ("marketplaceSubscription".equalsIgnoreCase(ch.getName())) {
                Object val = ch.getValue();
                if (val != null && "true".equalsIgnoreCase(val.toString())) return true;
            }
        }
        return false;
    }

    private String getMarketplaceId(Product p) {
        if (p.getRelatedParty() == null) return null;
        return p.getRelatedParty().stream()
                .filter(rp -> "Buyer".equalsIgnoreCase(rp.getRole()))
                .map(rp -> rp.getId())
                .findFirst().orElse(null);
    }

    private String format(Double value) {
        if (value == null) return "-";
        return String.format("%,.2f", value);
    }

    private double parseCurrency(String text) {
        if (text == null) return 0.0;
        String cleaned = text.replaceAll("[^0-9,\\.]", "").replace(",", ".");
        try { return Double.parseDouble(cleaned); } catch (NumberFormatException e) { return 0.0; }
    }

}
