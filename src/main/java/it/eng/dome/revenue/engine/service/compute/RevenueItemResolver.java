package it.eng.dome.revenue.engine.service.compute;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class RevenueItemResolver {

    private static final Logger logger = LoggerFactory.getLogger(RevenueItemResolver.class);

    private RevenueItem item;
    private Map<String, Double> replacements;
    private Map<String, String> calculatorContext;
    private PlanItem planItem;
    private Subscription subscription;
    private TimePeriod period;

    public RevenueItemResolver() {
    }

    public RevenueItemResolver setRevenueItem(RevenueItem item) {
        this.item = item;
        return this;
    }

    public RevenueItemResolver setReplacements(Map<String, Double> replacements) {
        this.replacements = replacements;
        return this;
    }

    public RevenueItemResolver setCalculatorContext(Map<String, String> context) {
        this.calculatorContext = context;
        return this;
    }

    public RevenueItemResolver setPlanItem(PlanItem planItem) {
        this.planItem = planItem;
        return this;
    }

    public RevenueItemResolver setSubscription(Subscription subscription) {
        this.subscription = subscription;
        return this;
    }

    public RevenueItemResolver setTimePeriod(TimePeriod period) {
        this.period = period;
        return this;
    }

    public RevenueItem getResolvedRevenueItem(RevenueItem item) {
        if (item != null) {
            String resolvedName = resolveString(item.getName());
            item.setName(resolvedName);
        }
        return item;
    }

    private String resolveString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String resolvedText = input;
        final Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z\\.\\-]+)\\}");
        Matcher matcher = pattern.matcher(input);

        Map<String, String> resolvedProperties = new HashMap<>();
        while (matcher.find()) {
            resolvedProperties.put(matcher.group(1), null);
        }

        // resolve each token
        for (String key : resolvedProperties.keySet()) {
            String resolvedValue = resolveToken(key);
            if (resolvedValue != null) {
                resolvedProperties.put(key, resolvedValue);
            } else {
                logger.error("Unresolved property {} in plan {}", key);
            }
        }

        // replace tokens
        for (Map.Entry<String, String> entry : resolvedProperties.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            resolvedText = resolvedText.replace("${" + entry.getKey() + "}", value);
        }

        return resolvedText;
    }

    private String resolveToken(String token) {
        switch(token) {
            case "plan.name":
                return this.subscription.getPlan().getName();
            case "subscription.name":
                return this.subscription.getName();
            case "subscription.startDate":
                return this.subscription.getStartDate().toString().substring(0, 10);
            case "chargetime":
                return this.item.getChargeTime().toString().substring(0, 10);
            case "chargePeriod.startDate":
                if(this.period!=null) {
                    OffsetDateTime odt = this.period.getStartDateTime();
                    if(odt!=null)
                        return odt.toString().substring(0, 10);
                }
                break;
            case "chargePeriod.endDate":
                if(this.period!=null) {
                    OffsetDateTime odt = this.period.getEndDateTime();
                    if(odt!=null)
                        return odt.toString().substring(0, 10);
                }
                break;
            case "seller.tradingname": 
                return this.subscription.getRelatedParties().stream()
                    .filter(rp -> "Seller".equalsIgnoreCase(rp.getRole()))
                    .map(rp -> rp.getName()) 
                    .findFirst()
                    .orElse("");
            // search in replacements
            default:
                Double d = this.replacements.get(token);
                if(d!=null)
                    return d.toString();
                else {
                    String s = this.calculatorContext.get(token);
                    if(s!=null)
                        return s;
                }
                break;
        }
        return null;
    }

}
