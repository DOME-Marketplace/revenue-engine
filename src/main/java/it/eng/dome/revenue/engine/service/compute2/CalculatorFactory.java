package it.eng.dome.revenue.engine.service.compute2;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.Subscription;

public class CalculatorFactory {

    public static Calculator getCalculatorFor(Subscription subscription, PlanItem item) {
        if(item.getIsBundle()) {
            return getBundleCalculatorFor(subscription, item);
        } else {
            return getAtomicCalculatorFor(subscription, item);
        }
    }

    private static Calculator getBundleCalculatorFor(Subscription subscription, PlanItem item) {
        if(item==null || !item.getIsBundle() || item.getBundleOp()==null)
            return null;
        switch(item.getBundleOp()) {
    		case CUMULATIVE:
                return new CumulativeCalculator(subscription, item);
            case ALTERNATIVE_HIGHER:
                return new AlternativeCalculator(subscription, item, item instanceof Price);
            case ALTERNATIVE_LOWER:
                return new AlternativeCalculator(subscription, item, !(item instanceof Price));
            case FOREACH:
                return new ForEachCalculator(subscription, item);
            default:
                throw new IllegalArgumentException("Unknown bundle operation: " + item.getBundleOp());
        }
    }

    private static Calculator getAtomicCalculatorFor(Subscription subscription, PlanItem item) {
        if(item==null || item.getIsBundle())
            return null;
        if(item instanceof Price) {
            return new AtomicPriceCalculator(subscription, (Price)item);
        } else if(item instanceof Discount) {
            return new AtomicDiscountCalculator(subscription, (Discount)item);            
        } else {
            throw new IllegalArgumentException("Can't instantiate a calculator for: " + item.getName());
        }
    }

}
