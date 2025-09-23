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
        this.resolve(plan.getPrice());
        return plan;
    }

    private PlanItem resolveDirectProperties(PlanItem item) {
        if(item!=null) {
            item.setIgnore(this.resolve(item.getIgnore()));
            item.setName(this.resolve(item.getName()));
        }
        return item;
    }

    private Price resolve(Price price) {
        if(price!=null) {
            this.resolveDirectProperties(price);
            if(price.getPrices()!=null) {
                for(Price p: price.getPrices()) {
                    this.resolve(p);
                }
            }
            this.resolve(price.getDiscount());
        }
        return price;
    }

    private Discount resolve(Discount discount) {
        if(discount!=null) {
            this.resolveDirectProperties(discount);
            discount.setIgnore(this.resolve(discount.getIgnore()));
            if(discount.getDiscounts()!=null) {
                for(Discount d: discount.getDiscounts()) {
                    this.resolve(d);
                }
            }
        }
        return discount;
    }

    private String resolve(String text) {
		final Pattern p = Pattern.compile("!?\\$\\{([a-zA-Z\\.]+)\\}");
		if(text!=null && !text.isEmpty()) {
			Matcher m = p.matcher(text);
			// identify properties to be resolved
			Map<String, String> resolvedProperties = new HashMap<>();
			while(m.find()) {
				resolvedProperties.put(m.group(1), null);
			}
			// now retrieve actual values
			for(String key: resolvedProperties.keySet()) {
				String resolvedValue = this.resolveToken(key);
				if(resolvedValue!=null) {
					resolvedProperties.put(key, resolvedValue);
                }
				else {
					logger.error("unresolved property {} in plan {}", key, this.subscription.getPlan().getName());
                }
			}
			// finally replace
			for(String key: resolvedProperties.keySet()) {
				String value = resolvedProperties.get(key);
				if(value==null)
					value = "<<ERROR.UNRESOLVED>>";
				text = text.replace("${"+key+"}", value);
			}
		}
		return text;
	}

	private String resolveToken(String token) {
        // product characteristics
		Pattern p1 = Pattern.compile("product.characteristic.([a-zA-Z]+)");
		Matcher m = p1.matcher(token);
		if(m.matches()) {
			if(this.subscription!=null) {
				return this.subscription.getCharacteristic(m.group(1));
			}
		}
        // directly properties
		Pattern p2 = Pattern.compile("product.([a-zA-Z]+)");
		m = p2.matcher(token);
		if(m.matches()) {
            if("name".equalsIgnoreCase(m.group(1)))
                return this.subscription.getName();
            if("startDate".equalsIgnoreCase(m.group(1)))
                return this.subscription.getStartDate().toString();
    		// TODO: add further patterns (e.g. product.<property>)... to be resolved with getters (with reflection?)
		}
        // plan properties
		Pattern p3 = Pattern.compile("plan.([a-zA-Z]+)");
		m = p3.matcher(token);
		if(m.matches()) {
            if("name".equalsIgnoreCase(m.group(1)))
                return this.subscription.getPlan().getName();
    		// TODO: add further plan properties...
		}
		return null;
	}

}
