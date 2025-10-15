package it.eng.dome.revenue.engine.service.compute;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class RevenueStatementBuilder {

    private static final Logger logger = LoggerFactory.getLogger(RevenueStatementBuilder.class);

    private Subscription subscription;

    public RevenueStatementBuilder(Subscription sub) {
        this.subscription = sub;
    }

	public RevenueStatement buildStatement(TimePeriod timePeriod) {
		logger.debug("Computing revenue statement for time: {}", timePeriod);

		if (this.subscription == null || this.subscription.getPlan() == null) {
			logger.error("Cannot compute - subscription or plan is null");
			return null;
		}

		try {
			RevenueStatement statement = new RevenueStatement(this.subscription, timePeriod);
			Price price = this.subscription.getPlan().getPrice();
			Calculator calc = CalculatorFactory.getCalculatorFor(this.subscription, price, null);
			RevenueItem revenueItem = calc.compute(timePeriod, new HashMap<>());
			if (revenueItem != null) {
				statement.addRevenueItem(revenueItem);
			} else {
				logger.info("No revenue items computed for plan: {}", this.subscription.getPlan().getName());
			}
			return statement;
		} catch (Exception e) {
			logger.error("Error computing revenue statement: {}", e.getMessage(), e);
		}

		return null;

	}
	
}
