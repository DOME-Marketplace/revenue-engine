package it.eng.dome.revenue.engine.service.compute;

import java.util.List;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.SubscriptionActive;

public class DiscountCalculator {

    private SubscriptionActive subscription;

    public DiscountCalculator(SubscriptionActive subscription) {
        this.subscription = subscription;
    }

    public Double compute(Discount discount) {

        // first compute the price
        Double discoutValue = 0.0;
        if(discount.getIsBundle()) {
            switch(discount.getBundleOp()) {
                case CUMULATIVE:
                    discoutValue = this.getCumulativeDiscount(discount.getDiscounts());
                case ALTERNATIVE_HIGHER:
                    discoutValue = this.getHigherDiscount(discount.getDiscounts());
                case ALTERNATIVE_LOWER:
                    discoutValue = this.getLowerDiscount(discount.getDiscounts());
                default:
                    throw new IllegalArgumentException("Unknown bundle operation: " + discount.getBundleOp());
            }

        } else {
            discoutValue = this.getAtomicDiscount(discount);
        }

        // TODO: apply constraints

        // return thee value;
        return discoutValue;
    }

        // assuming that the discount is atomic, compute the discount value
        private Double getAtomicDiscount(Discount discount) {
            // compute the period
            // retrive applicable base range
            // check applicableBase is in range (if any)
            // compute the discount (can be a percentage of the base or a fixed amount)
            // apply a constraint (min/max) on discount, if any
            return 0.0;
        }

        private Double getCumulativeDiscount(List<Discount> discounts) {
            // TODO: same as for prices
            return 0.0;
        }

        private Double getHigherDiscount(List<Discount> discounts) {
            // TODO: same as for prices
            return 0.0;
        }

        private Double getLowerDiscount(List<Discount> discounts) {
            // TODO: same as for prices
            return 0.0;
        }


}
