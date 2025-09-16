package it.eng.dome.revenue.engine.model.comparator;

import java.util.Comparator;

import it.eng.dome.revenue.engine.model.RevenueBill;

public class RevenueBillComparator implements Comparator<RevenueBill> {

    @Override
    public int compare(RevenueBill o1, RevenueBill o2) {
        int result = new TimePeriodComparator().compare(o1.getPeriod(), o2.getPeriod());
        if (result != 0)
            return result;
        return Integer.valueOf(o1.hashCode()).compareTo(o2.hashCode());
    }

}