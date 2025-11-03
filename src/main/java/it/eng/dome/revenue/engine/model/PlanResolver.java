package it.eng.dome.revenue.engine.model;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlanResolver {

    private static final Logger logger = LoggerFactory.getLogger(PlanResolver.class);

    private Subscription subscription;

    public PlanResolver(Subscription subscription) {
        this.subscription = subscription;
    }

    public Plan resolve(Plan plan) {
        if (plan != null) {
            resolvePrice(plan.getPrice());
        }
        return plan;
    }

    private PlanItem resolveDirectProperties(PlanItem item) {
        if (item != null) {
            String resolvedIgnore = resolveString(item.getIgnore());
            String resolvedName = resolveString(item.getName());
            item.setIgnore(resolvedIgnore);
            item.setName(resolvedName);
        }
        return item;
    }

    private Price resolvePrice(Price price) {
        if (price != null) {
            resolveDirectProperties(price);
            if (price.getPrices() != null) {
                for (Price p : price.getPrices()) {
                    resolvePrice(p);
                }
            }
            resolveDiscount(price.getDiscount());
        }
        return price;
    }

    private Discount resolveDiscount(Discount discount) {
        if (discount != null) {
            resolveDirectProperties(discount);
            discount.setIgnore(resolveString(discount.getIgnore()));
            if (discount.getDiscounts() != null) {
                for (Discount d : discount.getDiscounts()) {
                    resolveDiscount(d);
                }
            }
        }
        return discount;
    }

    private String resolveString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String resolvedText = input;
        final Pattern pattern = Pattern.compile("!?\\$\\{([a-zA-Z\\.]+)\\}");
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
                logger.error("Unresolved property {} in plan {}", key,
                        subscription != null && subscription.getPlan() != null
                                ? subscription.getPlan().getName()
                                : "<<UNKNOWN>>");
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
        // subscription characteristics
        Pattern p1 = Pattern.compile("subscription.characteristics.([a-zA-Z]+)");
        Matcher m = p1.matcher(token);
        if (m.matches() && subscription != null) {
            return subscription.getCharacteristics(m.group(1));
        }

        // subscription direct properties
        Pattern p2 = Pattern.compile("subscription.([a-zA-Z]+)");
        m = p2.matcher(token);
        if (m.matches() && subscription != null) {
            switch (m.group(1).toLowerCase()) {
                case "name":
                    return subscription.getName();
                case "startdate":
                    return subscription.getStartDate().toString();
                default:
                    break; // TODO: add more subscription properties if needed
            }
        }

        // plan properties
        Pattern p3 = Pattern.compile("plan.([a-zA-Z]+)");
        m = p3.matcher(token);
        if (m.matches() && subscription != null && subscription.getPlan() != null) {
            switch (m.group(1).toLowerCase()) {
                case "name":
                    return subscription.getPlan().getName();
                default:
                    break; // TODO: add more plan properties if needed
            }
        }
        
        Pattern p4 = Pattern.compile("seller\\.([a-zA-Z]+)");
        m = p4.matcher(token);
        if (m.matches() && subscription != null && subscription.getRelatedParties() != null) {
            switch (m.group(1).toLowerCase()) {
                case "tradingname": 
                    return subscription.getRelatedParties().stream()
                        .filter(rp -> "SellerOperator".equalsIgnoreCase(rp.getRole()))
                        .map(rp -> rp.getName()) 
                        .findFirst()
                        .orElse("");
                default:
                	break; // TODO: add more seller properties if needed
            }
        }

        return null;
    }
}
