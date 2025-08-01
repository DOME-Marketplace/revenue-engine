package it.eng.dome.revenue.engine.model.comparator;

import java.util.Comparator;

import it.eng.dome.revenue.engine.model.SimpleBill;

public class SimpleBillComparator implements Comparator<SimpleBill> {

    @Override
    public int compare(SimpleBill o1, SimpleBill o2) {
        int result = new TimePeriodComparator().compare(o1.getPeriod(), o2.getPeriod());
        if (result != 0)
            return result;
        return Integer.valueOf(o1.hashCode()).compareTo(o2.hashCode());
    }

}