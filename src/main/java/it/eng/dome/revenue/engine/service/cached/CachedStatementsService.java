package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;
import java.util.List;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.exception.BadRevenuePlanException;
import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.service.StatementsService;

@Service
public class CachedStatementsService extends StatementsService {

    private final Logger logger = LoggerFactory.getLogger(CachedStatementsService.class);

    @Value("${caching.revenue.enabled}")
    private Boolean REVENUE_CACHE_ENABLED;

    @Autowired
    CacheService cacheService;

    private Cache<String, List<RevenueStatement>> statementsCache;

    public CachedStatementsService() {
        super();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.initCaches();
    }

    private void initCaches() {
        this.statementsCache = this.cacheService.getOrCreateCache(
				"statementsCache",
				String.class,
				(Class<List<RevenueStatement>>)(Class<?>)List.class,
				Duration.ofHours(1));
    }

    /*
     * Retrieve bills from cache or from the parent class if not cached.
    */
    @Override
    public List<RevenueStatement> getStatementsForSubscription(String subscriptionId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        String key = subscriptionId;
        if (!REVENUE_CACHE_ENABLED || !this.statementsCache.containsKey(key)) {
            logger.debug("Cache MISS for subscription " + key);
            List<RevenueStatement> statements = super.getStatementsForSubscription(subscriptionId);
            this.statementsCache.put(key, statements);
        }
        return this.statementsCache.get(key);
    }

}
