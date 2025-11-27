package it.eng.dome.revenue.engine.service.compute;

import java.util.Map;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.service.MetricsRetriever;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public interface Calculator {

    public RevenueItem compute(TimePeriod timePeriod, Map<String, Double> computeContext) throws ExternalServiceException, BadTmfDataException;

    public void setMetricsRetriever(MetricsRetriever mr);

    public void setTmfDataRetriever(TmfDataRetriever tdr);

    public void setCalculatorContext(Map<String, String> calculatorContext);
    public Map<String, String> getCalculatorContext();

}
