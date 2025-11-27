package it.eng.dome.revenue.engine.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;


// FIXME: ACTIVE SUBSCRIPTIONS ONLY FOR NOW
@Service
public class ReportingService implements InitializingBean {

    protected final Logger logger = LoggerFactory.getLogger(ReportingService.class);

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
            double totalOverall = 0.0;
            String currency = String.valueOf("");

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

                    if (currency.isEmpty() && !items.isEmpty() && items.get(0).getCurrency() != null) {
                        currency = items.get(0).getCurrency() + " ";
                    }

                    Product product = tmfDataRetriever.getProduct(sub.getId(), null);
                    String buyerId = product.getRelatedParty().stream()
                            .filter(rp -> Role.BUYER.getValue().equalsIgnoreCase(rp.getRole()))
                            .map(rp -> rp.getId())
                            .findFirst().orElse("Unknown Subscriber");
                    String name = tmfDataRetriever.getOrganization(buyerId).getTradingName();

                    if (isFederated(product)) {
                        federatedProviders.add(new Report(name, currency + format(yearlyTotal)));
                        totalFederated += yearlyTotal;
                    } else {
                        cloudProviders.add(new Report(name, currency + format(yearlyTotal)));
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
                        + currency + format(totalCloud),
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
                        + currency + format(totalFederated),
                        federatedProviders
                ));

                List<Report> topFederated = federatedProviders.stream().limit(2).collect(Collectors.toList());
                result.add(new Report(
                        "Top Federated Marketplaces (" + periodStart + " - " + periodEnd + "): ",
                        topFederated
                ));
            }
            
            totalOverall = totalCloud + totalFederated;
            result.add(new Report(
                    "Overall Total Revenue (" + periodStart + " - " + periodEnd + "):",
                    currency + format(totalOverall)
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

            String startDate = th.getSubscriptionPeriodAt(OffsetDateTime.now()).getStartDateTime().toString();
            String renewalDate = th.getSubscriptionPeriodAt(OffsetDateTime.now()).getEndDateTime().toString();

            String agreementsText = Optional.ofNullable(plan.getAgreements())
                    .orElse(Collections.emptyList())
                    .stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .collect(Collectors.joining(". "));

            return new Report("My Subscription Plan", Arrays.asList(
                    new Report("Plan Name", planName),
                    new Report("Start Date", startDate),
                    new Report("Renewal Date", renewalDate),
                    new Report("Agreements and Discounts", agreementsText)
            ));
        } catch (BadTmfDataException | BadRevenuePlanException | ExternalServiceException e) {
            logger.error("getSubscriptionSection failed", e);
            return new Report("My Subscription Plan", "No data available: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getSubscriptionSection", e);
            return new Report("My Subscription Plan", "Error retrieving subscription information");
        }
    }

//    public Report getBillingHistorySection(String relatedPartyId) {
//        try {
//            Map<String, String> filter = new HashMap<>();
//            filter.put("relatedParty.id", relatedPartyId);
//            List<CustomerBill> allBills = tmfDataRetriever.getAllCustomerBills(null, filter, 100);
//
//            List<CustomerBill> buyerBills = allBills.stream()
//                    .filter(bill -> RelatedPartyUtils.customerBillHasPartyWithRole(bill, relatedPartyId, Role.BUYER))
//                    .sorted(new CustomerBillComparator())
//                    .collect(Collectors.toList());
//
//            List<Report> invoiceReports = new ArrayList<>();
//            for (CustomerBill cb : buyerBills) {
//                List<Report> details = new ArrayList<>();
//                if (cb.getRemainingAmount() != null) {
//                    double remaining = cb.getRemainingAmount().getValue();
//                    if (remaining > 0.0) details.add(new Report("Status", "Unpaid"));
//                    else if (remaining == 0.0) details.add(new Report("Status", "Paid"));
//                    else details.add(new Report("Status", "Unknown"));
//                } else if (cb.getTaxIncludedAmount() != null && cb.getAmountDue() != null &&
//                        cb.getTaxIncludedAmount().getValue() - cb.getAmountDue().getValue() > 0.0) {
//                    details.add(new Report("Status", "Partially Paid"));
//                }
//                if (cb.getTaxIncludedAmount() != null) {
//                    Float amount = cb.getTaxIncludedAmount().getValue();
//                    String unit = cb.getTaxIncludedAmount().getUnit() != null ? cb.getTaxIncludedAmount().getUnit() : "";
//                    details.add(new Report("Amount", String.format("%.2f %s", amount, unit)));
//                }
//                String periodText = cb.getBillDate() != null ? cb.getBillDate().toLocalDate().toString() : "Unknown Date";
//                invoiceReports.add(new Report("Invoice - " + periodText, details));
//            }
//
//            if (invoiceReports.isEmpty()) return new Report("Billing History", "No billing data available");
//            return new Report("Billing History", invoiceReports);
//        } catch (Exception e) {
//            return new Report("Billing History", "No billing data available");
//        }
//    }

    // FIXME: Note: For now, it's preferable to display the statements directly rather than using the method above
    public Report getBillingHistorySection(String relatedPartyId) {
        try {
            Subscription subscription = subscriptionService.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
            if (subscription == null)
                return new Report("Billing History", "No active subscription found");

            List<RevenueItem> statements = statementsService.getItemsForSubscription(subscription.getId());
            if (statements == null || statements.isEmpty())
                return new Report("Billing History", "No billing data available");

            statements.sort(Comparator.comparing(ri -> ri.getChargeTime()));

            List<Report> invoices = new ArrayList<>();
            for (RevenueItem ri : statements) {
                String date = ri.getChargeTime() != null
                        ? ri.getChargeTime().toLocalDate().toString()
                        : "Unknown Date";
                String amount = String.format("%.2f %s",
                        ri.getOverallValue(),
                        ri.getCurrency() != null ? ri.getCurrency() : "");


                List<Report> details = new ArrayList<>();

                details.add(new Report("Amount", amount));
                if( ri.getOverallValue()==0.0)
                	details.add(new Report("Status", "Paid"));
            	else
            		details.add(new Report("Status", "Unpaid"));

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
	        Subscription subscription = subscriptionService.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
	        if (subscription == null || subscription.getId() == null || subscription.getId().isEmpty())
	            return new Report("Revenue Volume Monitoring", List.of(new Report("Error", "Invalid subscription ID")));

	        List<RevenueItem> items = statementsService.getItemsForSubscription(subscription.getId());
	        if (items == null || items.isEmpty())
	            return new Report("Revenue Volume Monitoring", "No revenue data available");

	        LocalDate today = LocalDate.now();

	        LocalDate periodStart = today.minusMonths(1);
	        LocalDate periodEnd = today.plusMonths(0).withDayOfMonth(today.plusMonths(0).lengthOfMonth());

	        double yearlyTotal = 0.0;
	        double monthlyTotal = 0.0;
	        String currency = "";
	        String currentTier = "N/A";

	        for (RevenueItem ri : items) {
	            LocalDate chargeDate = ri.getChargeTime().toLocalDate();

	            if (currency.isEmpty() && ri.getCurrency() != null)
	                currency = ri.getCurrency() + " ";

	            yearlyTotal += ri.getOverallValue();

	            if (!chargeDate.isBefore(periodStart) && !chargeDate.isAfter(periodEnd)) {
	                monthlyTotal += ri.getOverallValue();

	                RevenueItem tierItem = ri.getItems().stream()
	                        .flatMap(i -> i.getItems().stream())
	                        .filter(i -> i.getOverallValue() > 0)
	                        .reduce((first, second) -> second) 
	                        .orElse(null);


	                if (tierItem != null)
	                    currentTier = extractRevenueSharePercentage(tierItem);
	            }
	        }

	        List<Report> reportItems = new ArrayList<>();
	        reportItems.add(new Report("Current Monthly Revenue (" + periodStart + " - " + periodEnd + ")", 
	                currency + format(monthlyTotal)));
	        reportItems.add(new Report("Current Tier", currentTier));
	        reportItems.add(new Report("Yearly Total", currency + format(yearlyTotal)));

	        return new Report("Revenue Volume Monitoring", reportItems);

	    } catch (BadTmfDataException | BadRevenuePlanException | ExternalServiceException e) {
	        logger.error("getRevenueSection failed", e);
	        return new Report("Revenue Volume Monitoring", "No data available: " + e.getMessage());
	    } catch (Exception e) {
	        logger.error("Unexpected error in getRevenueSection", e);
	        return new Report("Revenue Volume Monitoring", "Error retrieving revenue information");
	    }
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

    private String extractRevenueSharePercentage(RevenueItem item) {
        if (item == null) return "N/A";
        String found = "N/A";

        if (item.getName() != null && item.getName().matches(".*\\d+%.*")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)%").matcher(item.getName());
            while (m.find()) found = m.group(1) + "%";
        }

        if (item.getItems() != null && !item.getItems().isEmpty()) {
            for (RevenueItem sub : item.getItems()) {
                String subPct = extractRevenueSharePercentage(sub);
                if (!"N/A".equals(subPct)) found = subPct;
            }
        }

        return found;
    }
}
