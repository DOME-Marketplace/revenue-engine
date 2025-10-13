package it.eng.dome.revenue.engine.service.compute2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.MetricsRetriever;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;

@Component
public class CalculatorFactory implements InitializingBean{

    private static final Logger logger = LoggerFactory.getLogger(CalculatorFactory.class);

    private static MetricsRetriever staticMR;
    private static TmfCachedDataRetriever staticTDR;

    @Autowired
    private MetricsRetriever mr;

    @Autowired
    private TmfCachedDataRetriever tdr;

    public static Calculator getCalculatorFor(Subscription subscription, PlanItem item) {
        logger.debug("*************** Calculator FACTORY **************");
        Calculator c;
        if(item.getIsBundle()) {
            c = getBundleCalculatorFor(subscription, item);
        } else {
            c = getAtomicCalculatorFor(subscription, item);
        }
        if(c!=null) {
            c.setMetricsRetriever(CalculatorFactory.staticMR);
            c.setTmfDataRetriever(CalculatorFactory.staticTDR);
        }
        return c;
    }

    private static Calculator getBundleCalculatorFor(Subscription subscription, PlanItem item) {
        if(item==null || !item.getIsBundle() || item.getBundleOp()==null)
            return null;
        switch(item.getBundleOp()) {
    		case CUMULATIVE:
                logger.debug("creating CUMULATIVE calculator for {}", item.getName());
                return new CumulativeCalculator(subscription, item);
            case ALTERNATIVE_HIGHER:
                logger.debug("creating ALTERNATIVE_HIGHER calculator for {}", item.getName());
                return new AlternativeCalculator(subscription, item, item instanceof Price);
            case ALTERNATIVE_LOWER:
                logger.debug("creating ALTERNATIVE_LOWER calculator for {}", item.getName());
                return new AlternativeCalculator(subscription, item, !(item instanceof Price));
            case FOREACH:
                logger.debug("creating FOREACH calculator for {}", item.getName());
                return  new ForEachCalculator(subscription, item);
            default:
                throw new IllegalArgumentException("Unknown bundle operation: " + item.getBundleOp());
        }
    }

    private static Calculator getAtomicCalculatorFor(Subscription subscription, PlanItem item) {
        if(item==null || item.getIsBundle())
            return null;
        if(item instanceof Price) {
            logger.debug("creating AtomicPriceCalculator for {}", item.getName());
            return new AtomicPriceCalculator(subscription, (Price)item);
        } else if(item instanceof Discount) {
            logger.debug("creating AtomicDiscountCalculator for {}", item.getName());
            return new AtomicDiscountCalculator(subscription, (Discount)item);
        } else {
            throw new IllegalArgumentException("Can't instantiate a calculator for: " + item.getName());
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        CalculatorFactory.staticMR = this.mr;
        CalculatorFactory.staticTDR = this.tdr;
    }

}
