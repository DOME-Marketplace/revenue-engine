package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;
import java.util.List;


import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Service
public class TmfCachedDataRetriever extends TmfDataRetriever {

    private final Logger logger = LoggerFactory.getLogger(TmfCachedDataRetriever.class);

    @Value("${caching.tmf.enabled}")
    private Boolean TMF_CACHE_ENABLED;

    @Autowired
    CacheService cacheService;

    private Cache<String, List<AppliedCustomerBillingRate>> acbrCache;
    private Cache<String, BillingAccountRef> billingAccountCache;

    public TmfCachedDataRetriever() {
        super();
    }

        @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.initCaches();
    }

    private void initCaches() {

        logger.debug("*********** PF ************* {}", TMF_CACHE_ENABLED);

        this.acbrCache = this.cacheService.getOrCreateCache(
				"acbrCache",
				String.class,
				(Class<List<AppliedCustomerBillingRate>>)(Class<?>)List.class,
				Duration.ofHours(1));

        this.billingAccountCache = this.cacheService.getOrCreateCache(
				"billingAccountCache",
				String.class,
				BillingAccountRef.class,
				Duration.ofHours(1));
    }

    @Override
    public List<AppliedCustomerBillingRate> retrieveBills(String sellerId, TimePeriod timePeriod, Boolean isBilled) throws Exception {
        String key = sellerId + timePeriod.toString() + isBilled.toString();
        if (!TMF_CACHE_ENABLED || !this.acbrCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            List<AppliedCustomerBillingRate> acbrs = super.retrieveBills(sellerId, timePeriod, isBilled);
            this.acbrCache.put(key, acbrs);
        }
        return this.acbrCache.get(key);
    }

    @Override
    public BillingAccountRef retrieveBillingAccountByProductId(String productId) {
        String key = productId;
        if (!TMF_CACHE_ENABLED || !this.billingAccountCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            BillingAccountRef billingAccountRef = super.retrieveBillingAccountByProductId(productId);
            this.billingAccountCache.put(key, billingAccountRef);
        }
        return this.billingAccountCache.get(key);
    }

}
