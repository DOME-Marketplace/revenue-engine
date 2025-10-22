package it.eng.dome.revenue.engine.exception;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.PlanItem;

public class BadRevenuePlanException extends Exception {

    public BadRevenuePlanException(Plan item, String message, Throwable cause) {
        super(String.format("Subscription plan named '%s' has the following issue: %s", item.getName(), message), cause);
    }

    public BadRevenuePlanException(PlanItem item, String message, Throwable cause) {
        super(String.format("Plan item named '%s' has the following issue: %s", item.getName(), message), cause);
    }

    public BadRevenuePlanException(Plan item, String message) {
		super(String.format("Subscription plan named '%s' has the following issue: %s", item.getName(), message));

    }

    public BadRevenuePlanException(PlanItem item, String message) {		
    	super(String.format("Plan item named '%s' has the following issue: %s", item.getName(), message));

	}	
}
