package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;
import java.util.List;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.SubscriptionService;

@Service
public class CachedSubscriptionService extends SubscriptionService {

    private final Logger logger = LoggerFactory.getLogger(CachedSubscriptionService.class);

    @Autowired
    CacheService cacheService;

    private Cache<String, List<Subscription>> subscriptionsCache;

    public CachedSubscriptionService() {
        super();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.initCaches();
    }

    private void initCaches() {
        logger.info("****** " + this.cacheService);
        this.subscriptionsCache = this.cacheService.getOrCreateCache(
				"subscriptionsCache",
				String.class,
				(Class<List<Subscription>>)(Class<?>)List.class,
				Duration.ofHours(1));
    }

    /*
     * Retrieve bills from cache or from the parent class if not cached.
    */
    @Override
    public List<Subscription> getAllSubscriptionsByProducts() {
        String key = "all_subscriptions";
        if (!this.subscriptionsCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            List<Subscription> subscriptions = super.getAllSubscriptionsByProducts();
            this.subscriptionsCache.put(key, subscriptions);
        }
        return this.subscriptionsCache.get(key);
    }

}
