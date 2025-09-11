package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;
import java.util.List;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.service.StatementsService;

class RevenueStatementsList {

    private List<RevenueStatement> items;

    public RevenueStatementsList(List<RevenueStatement> items) {
        this.items = items;
    }

    public List<RevenueStatement> getItems() {
        return this.items;
    }
}

@Service
public class CachedStatementsService extends StatementsService {

    private final Logger logger = LoggerFactory.getLogger(CachedStatementsService.class);

    private CacheManager cacheManager;

    private Cache<String, RevenueStatementsList> statementsCache;

    public CachedStatementsService() {
        super();
        this.initCaches();
    }

    private void initCaches() {

        this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
        this.cacheManager.init();

        // size: 200, ttl: 1 hour
        CacheConfiguration<String, RevenueStatementsList> cconfig = CacheConfigurationBuilder
                .newCacheConfigurationBuilder(String.class, RevenueStatementsList.class, ResourcePoolsBuilder.heap(100))
                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(30)))
                .build();
        this.statementsCache = this.cacheManager.createCache("statementsCache", cconfig);
        logger.info("Cache 'subscriptionsCache' initialized with size 10 and TTL of 30 minutes");
    }

    /*
     * Retrieve bills from cache or from the parent class if not cached.
    */
    @Override
    public List<RevenueStatement> getStatementsForSubscription(String subscriptionId) throws Exception {
        String key = subscriptionId;
        if (!this.statementsCache.containsKey(key)) {
            logger.debug("Cache MISS for subscription " + key);
            List<RevenueStatement> subscriptions = super.getStatementsForSubscription(subscriptionId);
            this.statementsCache.put(key, new RevenueStatementsList(subscriptions));
        }
        return this.statementsCache.get(key).getItems();
    }

}
