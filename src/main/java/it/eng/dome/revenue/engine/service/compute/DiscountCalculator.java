package it.eng.dome.revenue.engine.service.compute;

import java.time.OffsetDateTime;
import java.util.List;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.Subscription;

public class DiscountCalculator {

    private Subscription subscription;

    public DiscountCalculator(Subscription subscription) {
        this.subscription = subscription;
    }

    // this is the main method to be called by some extenal service
    // the caller will pick a discount in the subscription plan and ask this to compute the discount
    public Double compute(Discount discount, OffsetDateTime time) {
     
     if(discount == null) {
      return 0.0;
     }
     
     // first compute the price
        Double discoutValue = 0.0;
        if (Boolean.TRUE.equals(discount.getIsBundle())) {
            switch(discount.getBundleOp()) {
                case CUMULATIVE:
                    discoutValue = this.getCumulativeDiscount(discount.getDiscounts(), time);
                    break;
                case ALTERNATIVE_HIGHER:
                    discoutValue = this.getHigherDiscount(discount.getDiscounts(), time);
                    break;
                case ALTERNATIVE_LOWER:
                    discoutValue = this.getLowerDiscount(discount.getDiscounts(), time);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown bundle operation: " + discount.getBundleOp());
            }

        } else {
            discoutValue = this.getAtomicDiscount(discount, time);
        }

        // TODO: apply constraints ???

        return discoutValue;
    }

        // assuming that the discount is atomic, compute the discount value
        private Double getAtomicDiscount(Discount discount, OffsetDateTime time) {
            // TODO: remove the below. Just to avoid PMD complaining about unused variables
            //Boolean isBundle = discount.getIsBundle();
            //time.getYear();
            // compute the period
            // retrive applicable base range
            // check applicableBase is in range (if any)
            // compute the discount (can be a percentage of the base or a fixed amount)
            // apply a constraint (min/max) on discount, if any
            // Check if the base is within the valid range (if applicable)


         if (discount.getPercent() != null) {
                return discount.getPercent();
            }
         return 0.0;
        }

        private Double getCumulativeDiscount(List<Discount> discounts, OffsetDateTime time) {
            Double cumulativePrice = 0.0;
            for (Discount d : discounts) {
                cumulativePrice += this.compute(d, time);
            }
            return cumulativePrice;
        }

        private Double getHigherDiscount(List<Discount> discounts, OffsetDateTime time) {
            Double higher = 0.0;
            for (Discount d : discounts) {
                Double pValue = this.compute(d, time);
                higher = Math.max(higher, pValue);
            }
            return higher;
        }

        private Double getLowerDiscount(List<Discount> discounts, OffsetDateTime time) {
            Double higher = 0.0;
            for (Discount d : discounts) {
                Double pValue = this.compute(d, time);
                higher = Math.min(higher, pValue);
            }
            return higher;
        }
        
        private Double getApplicableBaseAmount(Discount discount) {
            String base = discount.getComputationBase();
            if (base == null) return null;

            switch (base) {
                case "price":
                    if (discount.getParentPrice() != null) {
                        return discount.getParentPrice().getAmount();
                    }
                    break;
                // In futuro puoi aggiungere altri tipi di base
                default:
                    throw new IllegalArgumentException("Unknown computation base: " + base);
            }

            return null;
        }
}

 