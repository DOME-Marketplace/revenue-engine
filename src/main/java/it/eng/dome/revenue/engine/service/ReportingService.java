package it.eng.dome.revenue.engine.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.exception.BadRevenuePlanException;
import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Report;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.model.comparator.CustomerBillComparator;
import it.eng.dome.revenue.engine.service.cached.CachedPlanService;
import it.eng.dome.revenue.engine.service.cached.CachedStatementsService;
import it.eng.dome.revenue.engine.service.cached.CachedSubscriptionService;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.utils.RelatedPartyUtils;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

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

    public void afterPropertiesSet() {}

    public ReportingService() {}

    /**
     * Retrieves the complete dashboard report for a given organization, using cache to avoid repeated computations.
     *
     * @param relatedPartyId the organization ID
     * @return a list of Report sections for the dashboard
     * @throws BadTmfDataException if TMF data retrieval fails
     * @throws BadRevenuePlanException if revenue plan is invalid
     * @throws ExternalServiceException if external service call fails
     */
    public List<Report> getDashboardReport(String relatedPartyId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        logger.info("Reporting for dashboard, Organization ID = {}", relatedPartyId);
        if (relatedPartyId == null || relatedPartyId.isEmpty()) {
			throw new BadTmfDataException("Organization", relatedPartyId, "Related Party ID cannot be null or empty");
		}
        
        List<Report> report = new ArrayList<>();
		
		List<Product> products = tmfDataRetriever.getAllSubscriptionProducts();
		
		boolean isDomeOp = false;
		
		// TODO: REPLACE WITH ROLE.DOME_OPERATOR
		for(Product p : products) {
			if(RelatedPartyUtils.productHasPartyWithRole(p, relatedPartyId, Role.SELLER_OPERATOR)) {
				isDomeOp = true;
				break;
			}
		}		
		
		if(!isDomeOp) {
	        // My Subscription Plan
	        report.add(getSubscriptionSection(relatedPartyId));
	
	        // Billing History
	        report.add(getBillingHistorySection(relatedPartyId));
	
	        // Revenue section
	        report.add(getRevenueSection(relatedPartyId));
		}
		else {
			Report totalRevenueReport = totalSubscriptionRevenueSection();
	        report.add(totalRevenueReport);

			// active providers
	        report.add(activeProvidersSection());
			
			// top performing providers
	        report.add(topSubscriptionsSection(totalRevenueReport));
		}
		
        return report;
    }

    /**
     * Retrieves the subscription section for the given relatedPartyId.
     * 
     * @param relatedPartyId the ID of the related party
     * @return a Report object containing subscription details
     * @throws BadTmfDataException if TMF data retrieval fails
     * @throws BadRevenuePlanException if revenue plan is invalid
     * @throws ExternalServiceException if external service call fails
     */
    public Report getSubscriptionSection(String relatedPartyId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        try {
            Subscription subscription = subscriptionService.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
            if (subscription == null) {
                return new Report("Subscription", "No active subscription found for this user.");
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
                    .map(agreement -> "" + agreement.trim())  
                    .collect(Collectors.joining(". ")); 

            return new Report("My Subscription Plan", Arrays.asList(
                new Report("Plan Name", planName),
                new Report("Start Date", startDate),
                new Report("Renewal Date", renewalDate),
                new Report("Agreements and Discounts", agreementsText)
            ));
        } catch (BadTmfDataException | BadRevenuePlanException | ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in getSubscriptionSection for relatedPartyId {}: {}", relatedPartyId, e.getMessage(), e);
            throw new ExternalServiceException("Unexpected error retrieving subscription section");
        }
    }

    /**
     * Retrieves the billing history section for the given relatedPartyId.
     *
     * @param relatedPartyId the ID of the related party
     * @return a Report object containing billing history details
     */
    public Report getBillingHistorySection(String relatedPartyId) {
        try {
            // retrieve all bills
            Map<String, String> filter = new HashMap<>();
            filter.put("relatedParty.id", relatedPartyId);
            List<CustomerBill> allBills = tmfDataRetriever.getAllCustomerBills(null, filter);

            List<CustomerBill> buyerBills = new ArrayList<>();
            for (CustomerBill bill : allBills) {
                if (RelatedPartyUtils.customerBillHasPartyWithRole(bill, relatedPartyId, Role.BUYER)) {
                    buyerBills.add(bill);
                }
            }

            Collections.sort(buyerBills, new CustomerBillComparator());

            List<Report> invoiceReports = new ArrayList<>();

            for (CustomerBill cb : buyerBills) {
                List<Report> details = new ArrayList<>();

                // bill status
                if (cb.getRemainingAmount() != null) {
                    double remaining = cb.getRemainingAmount().getValue();
                    if (remaining > 0.0) {
                        details.add(new Report("Status", "Unpaid"));
                    } else if (remaining == 0.0) {
                        details.add(new Report("Status", "Paid"));
                    } else {
                        details.add(new Report("Status", "Unknown"));
                    }
                } else if (cb.getTaxIncludedAmount() != null && cb.getAmountDue() != null &&
                           cb.getTaxIncludedAmount().getValue() - cb.getAmountDue().getValue() > 0.0) {
                    details.add(new Report("Status", "Partially Paid"));
                }

                // amount
                if (cb.getTaxIncludedAmount() != null) {
                    Float amount = cb.getTaxIncludedAmount().getValue();
                    String unit = cb.getTaxIncludedAmount().getUnit() != null ? cb.getTaxIncludedAmount().getUnit() : "";
                    details.add(new Report("Amount", String.format("%.2f %s", amount, unit)));
                }

                // billing period
                String periodText = cb.getBillDate() != null
                        ? cb.getBillDate().toLocalDate().toString()
                        : "Unknown Date";

                String label = "Invoice - " + periodText;
                invoiceReports.add(new Report(label, details));
            }

            if (invoiceReports.isEmpty()) {
                return new Report("Billing History", "No billing data available");
            }

            return new Report("Billing History", invoiceReports);
        } catch (Exception e) {
            logger.error("Error retrieving billing history for relatedPartyId {}: {}", relatedPartyId, e.getMessage(), e);
            return new Report("Billing History", "No billing data available");
        }
    }

    /** Retrieves the revenue section for the given relatedPartyId.
     * 
     * @param relatedPartyId the ID of the related party
     * @return a Report object containing revenue details
     * @throws BadTmfDataException if TMF data retrieval fails
     * @throws BadRevenuePlanException if revenue plan is invalid
     * @throws ExternalServiceException if external service call fails
    */
    public Report getRevenueSection(String relatedPartyId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        try {
            Subscription subscription = subscriptionService.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
            if (subscription == null || subscription.getId() == null || subscription.getId().isEmpty()) {
                logger.warn("Subscription ID is null or not found for Organization with ID: {}", relatedPartyId);
                return new Report(
                    "Revenue Volume Monitoring",
                    List.of(new Report("Error", "Invalid subscription ID"))
                );
            }

            String subscriptionId = subscription.getId();
            List<RevenueItem> items = statementsService.getItemsForSubscription(subscriptionId);

            if (items == null || items.isEmpty()) {
                return new Report("Revenue Volume Monitoring", "No revenue data available");
            }

            LocalDate today = LocalDate.now();
            double yearlyTotal = 0.0;
            double monthlyTotal = 0.0;
            String currency = "";
            String currentTier = "0% commission";

            for (RevenueItem ri : items) {
                LocalDate chargeDate = ri.getChargeTime().toLocalDate();
                if (chargeDate.isAfter(today)) continue; // skip future charges

                if (currency.isEmpty() && ri.getCurrency() != null) {
                    currency = ri.getCurrency() + " ";
                }

                yearlyTotal += ri.getOverallValue();

                if (chargeDate.getMonth() == today.getMonth() && chargeDate.getYear() == today.getYear()) {
                    monthlyTotal += ri.getOverallValue();

                    RevenueItem tierItem = ri.getItems().stream()
                            .flatMap(i -> i.getItems().stream())
                            .filter(i -> i.getOverallValue() > 0)
                            .findFirst()
                            .orElse(null);
                    if (tierItem != null) {
                        currentTier = extractRevenueSharePercentage(tierItem) + " commission";
                    }
                }
            }

            List<Report> reportItems = new ArrayList<>();
            reportItems.add(new Report("Current Monthly Revenue: ", currency + format(monthlyTotal)));
            reportItems.add(new Report("Current Tier: ", currentTier));
            reportItems.add(new Report("Yearly Total: ", currency + format(yearlyTotal)));

            return new Report("Revenue Volume Monitoring", reportItems);

        } catch (BadTmfDataException | BadRevenuePlanException | ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error in getRevenueSection for relatedPartyId {}: {}", relatedPartyId, e.getMessage(), e);
            throw new ExternalServiceException("Unexpected error retrieving revenue section");
        }
    }
    
    private Report topSubscriptionsSection(Report totalRevenueReport) {
        if (totalRevenueReport.getItems() == null || totalRevenueReport.getItems().isEmpty()) {
            return new Report("Top Subscriptions", "No revenue data available");
        }

        List<Report> subscriptions = totalRevenueReport.getItems().stream()
                .filter(r -> !"Overall Total".equalsIgnoreCase(r.getLabel()))
                .collect(Collectors.toList());

        if (subscriptions.isEmpty()) {
            return new Report("Top Subscription", "No revenue data available");
        }

        subscriptions.sort((r1, r2) -> Double.compare(parseCurrency(r2.getText()), parseCurrency(r1.getText())));

        return new Report("Top Subscriptions", List.of(subscriptions.get(0),subscriptions.get(1)));
    }

    public Report activeProvidersSection() throws ExternalServiceException, BadTmfDataException {
		
    	// FIXME: ACTIVE SUBSCRIPTIONS ONLY
		int nr = subscriptionService.getAllSubscriptions().size();
        
		return new Report("Active Providers", String.valueOf(nr));
			
	}
    
    public Report totalSubscriptionRevenueSection()
            throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {

    	// FIXME: ACTIVE SUBSCRIPTIONS ONLY
        List<Subscription> subscriptions = subscriptionService.getAllSubscriptions();
        if (subscriptions == null || subscriptions.isEmpty()) {
            return new Report("Total Subscription Revenue", "No active subscriptions found");
        }

        List<Report> subscriptionReports = new ArrayList<>();
        double totalOverall = 0.0;
        String currency = "";

        LocalDate today = LocalDate.now();

        for (Subscription sub : subscriptions) {
            String subId = sub.getId();
            if (subId == null || subId.isEmpty()) continue;

            try {
                List<RevenueItem> items = statementsService.getItemsForSubscription(subId);
                if (items == null || items.isEmpty()) continue;

                double yearlyTotal = 0.0;

                for (RevenueItem ri : items) {
                    LocalDate chargeDate = ri.getChargeTime().toLocalDate();
                    if (chargeDate.isAfter(today)) continue;

                    if (currency.isEmpty() && ri.getCurrency() != null) {
                        currency = ri.getCurrency() + " ";
                    }

                    yearlyTotal += ri.getOverallValue();
                }

                totalOverall += yearlyTotal;

                String subName = sub.getName();
                subscriptionReports.add(new Report(subName, currency + format(yearlyTotal)));

            } catch (Exception e) {
                logger.warn("Skipping subscription {} due to error: {}", subId, e.getMessage());
            }
        }

        if (subscriptionReports.isEmpty()) {
            return new Report("Total Subscription Revenue", "No revenue data available");
        }

        subscriptionReports.add(new Report("Overall Total", currency + format(totalOverall)));

        return new Report("Total Subscription Revenue", subscriptionReports);
    }



    /**
     * Retrieves revenue statements for the given relatedPartyId.
     * 
     * @param relatedPartyId the ID of the related party
     * @return a list of RevenueStatement objects
     * @throws BadTmfDataException if TMF data retrieval fails
     * @throws ExternalServiceException if external service call fails
     */
    public List<RevenueItem> getRevenueStatements(String relatedPartyId) throws BadTmfDataException, ExternalServiceException {
        logger.info("Call getRevenueStatements with relatedPartyId: {}", relatedPartyId);

        try {
            Subscription subscription = subscriptionService.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
            String subscriptionId = subscription.getId();
            logger.debug("Retrieved subscriptionId: {}", subscriptionId);
            return statementsService.getItemsForSubscription(subscriptionId);
        } catch (Exception e) {
            logger.error("Error retrieving statements for relatedPartyId {}: {}", relatedPartyId, e.getMessage(), e);
            throw new ExternalServiceException("Unexpected error retrieving statements");
        }
    }

    private String extractRevenueSharePercentage(RevenueItem item) {
        if (item == null) return "0%";

        if (item.getValue() != null && item.getValue() > 0 && item.getName() != null && item.getName().contains("%")) {
            String name = item.getName();
            int percentIndex = name.indexOf("%");
            if (percentIndex > 0) {
                String beforePercent = name.substring(0, percentIndex);
                String[] parts = beforePercent.split(" ");
                return parts[parts.length - 1] + "%";
            }
        }
        if (item.getItems() != null) {
            for (RevenueItem subItem : item.getItems()) {
                String percentage = extractRevenueSharePercentage(subItem);
                if (!"0%".equals(percentage)) {
                    return percentage;
                }
            }
        }

        return "0%";
    }

    private String format(Double value) {
        if (value == null) return "-";
        return String.format("%,.2f", value);
    }


    private double parseCurrency(String text) {
        if (text == null) return 0.0;
        String cleaned = text.replaceAll("[^0-9,\\.]", "").replace(",", ".");
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

   
}
