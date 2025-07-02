package it.eng.dome.revenue.engine.service.compute;

import java.util.List;

import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.SubscriptionActive;

public class PriceCalculator {

        private SubscriptionActive subscription;

        public Double compute(Price price) {

            // first compute the price
            Double priceValue = 0.0;
            if(price.getIsBundle()) {
                switch(price.getBundleOp()) {
                    case CUMULATIVE:
                        priceValue = this.getCumulativePrice(price.getPrices());
                    case ALTERNATIVE_HIGHER:
                        priceValue = this.getHigherPrice(price.getPrices());
                    case ALTERNATIVE_LOWER:
                        priceValue = this.getLowerPrice(price.getPrices());
                    default:
                        throw new IllegalArgumentException("Unknown bundle operation: " + price.getBundleOp());
                }

            } else {
                priceValue = this.getAtomicPrice(price);
            }

            // then apply the discount
            if(price.getDiscounts()!=null && !price.getDiscounts().isEmpty()) {
                DiscountCalculator discountCalculator = new DiscountCalculator(subscription);
                // TODO: compute the discount
                // TODO: reduce the price by the discount (it can be absolute or percentage)
            }

            // TODO: constraint to price range

            // return thee value;
            return priceValue;
        }

        // assuming that the price is atomic, compute the price value
        private Double getAtomicPrice(Price price) {
            // compute the period
            // retrive applicable base range
            // check applicableBase is in range (if any)
            // compute the price (can be a percentage of the base or a fixed amount)
            // apply a constraint (min/max) on price, if any
            return 0.0;
        }

        private Double getCumulativePrice(List<Price> prices) {
            Double cumulativePrice = 0.0;
            for (Price p : prices) {
                cumulativePrice += this.compute(p);
            }
            return cumulativePrice;
        }

        private Double getHigherPrice(List<Price> prices) {
            Double higher = 0.0;
            for (Price p : prices) {
                Double pValue = this.compute(p);
                higher = Math.max(higher, pValue);
            }
            return higher;
        }

        private Double getLowerPrice(List<Price> prices) {
            Double lower = 0.0;
            for (Price p : prices) {
                Double pValue = this.compute(p);
                lower = Math.min(lower, pValue);
            }
            return lower;
        }

}
