package it.eng.dome.revenue.engine.model.comparator;

import java.util.Comparator;

import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class TimePeriodComparator implements Comparator<TimePeriod> {

    @Override
    public int compare(TimePeriod o1, TimePeriod o2) {
        int result = o1.getStartDateTime().compareTo(o2.getStartDateTime());
        if (result != 0)
            return result;
        return o1.getEndDateTime().compareTo(o2.getEndDateTime());
    }

}