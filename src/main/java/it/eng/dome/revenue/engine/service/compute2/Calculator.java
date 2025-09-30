package it.eng.dome.revenue.engine.service.compute2;

import java.util.Map;

import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public interface Calculator {

    public RevenueItem compute(TimePeriod timePeriod, Map<String, Double> computeContext);

}
