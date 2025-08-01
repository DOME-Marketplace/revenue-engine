package it.eng.dome.revenue.engine.model.comparator;

import java.util.Comparator;

import it.eng.dome.revenue.engine.model.RevenueItem;

public class RevenueItemComparator implements Comparator<RevenueItem> {

    @Override
    public int compare(RevenueItem o1, RevenueItem o2) {
        // compare by chargeTime
        int result = o1.getChargeTime().compareTo(o2.getChargeTime());
        if (result != 0)
            return result;
        // compare by name
        result = o1.getName().compareTo(o2.getName());
        if (result != 0)
            return result;
        // compare by value
        return o1.getValue().compareTo(o2.getValue());
    }
}
