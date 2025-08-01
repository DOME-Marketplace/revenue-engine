package it.eng.dome.revenue.engine.model.comparator;

import java.util.Comparator;

import it.eng.dome.revenue.engine.model.RevenueStatement;

public class RevenueStatementTimeComparator implements Comparator<RevenueStatement> {

    @Override
    public int compare(RevenueStatement o1, RevenueStatement o2) {
        // compare by time coverage
        int result = new TimePeriodComparator().compare(o1.getPeriod(), o2.getPeriod());
        if (result != 0)
            return result;
        // compare by time of the subscription
        result = o1.getSubscription().getStartDate().compareTo(o2.getSubscription().getStartDate());
        if (result != 0)
            return result;
        // compare by subscription
        return o1.getSubscription().getId().compareTo(o2.getSubscription().getId());
    }
    
}
