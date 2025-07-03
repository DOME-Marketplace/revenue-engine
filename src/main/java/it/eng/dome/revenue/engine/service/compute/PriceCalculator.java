package it.eng.dome.revenue.engine.service.compute;

import java.time.OffsetDateTime;
import java.util.List;

import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.Subscription;

public class PriceCalculator {

        private Subscription subscription;


        public Double compute(OffsetDateTime time) {
            // TODO: plan.price should be a single item, not a list
            return this.compute(subscription.getPlan().getPrice(), time);
        }
 
        // this is the main method to be called by some extenal service
        // the caller will pick a price in the subscription plan and ask this to compute the price
        public Double compute(Price price, OffsetDateTime time) {

            // first compute the price
            Double priceValue = 0.0;
            if(price.getIsBundle()) {
                switch(price.getBundleOp()) {
                    case CUMULATIVE:
                        priceValue = this.getCumulativePrice(price.getPrices(), time);
                        break;
                    case ALTERNATIVE_HIGHER:
                        priceValue = this.getHigherPrice(price.getPrices(), time);
                        break;
                    case ALTERNATIVE_LOWER:
                        priceValue = this.getLowerPrice(price.getPrices(), time);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown bundle operation: " + price.getBundleOp());
                }

            } else {
                priceValue = this.getAtomicPrice(price, time);
            }

            // then apply the discount
            // TODO: check if condition
            if(price.getDiscount()!=null && !price.getDiscount().getDiscounts().isEmpty()) {
                DiscountCalculator discountCalculator = new DiscountCalculator(subscription);
                // TODO: compute the discount
                discountCalculator.compute(price.getDiscount(), time);
                // TODO: reduce the price by the discount (it can be absolute or percentage)
            }

            // TODO: constraint to price range

            // return thee value;
            return priceValue;
        }

        // assuming that the price is atomic, compute the price value
        private Double getAtomicPrice(Price price, OffsetDateTime time) {
            // TODO: remove the below. Just to avoid PMD complaining about unused variables
            price.getIsBundle();
            time.getYear();
            // compute the period
            // retrive applicable base range
            // check applicableBase is in range (if any)
            // compute the price (can be a percentage of the base or a fixed amount)
            // apply a constraint (min/max) on price, if any
            return 0.0;
        }

        private Double getCumulativePrice(List<Price> prices, OffsetDateTime time) {
            Double cumulativePrice = 0.0;
            for (Price p : prices) {
                cumulativePrice += this.compute(p, time);
            }
            return cumulativePrice;
        }

        private Double getHigherPrice(List<Price> prices, OffsetDateTime time) {
            Double higher = 0.0;
            for (Price p : prices) {
                Double pValue = this.compute(p, time);
                higher = Math.max(higher, pValue);
            }
            return higher;
        }

        private Double getLowerPrice(List<Price> prices, OffsetDateTime time) {
            Double lower = 0.0;
            for (Price p : prices) {
                Double pValue = this.compute(p, time);
                lower = Math.min(lower, pValue);
            }
            return lower;
        }

}
