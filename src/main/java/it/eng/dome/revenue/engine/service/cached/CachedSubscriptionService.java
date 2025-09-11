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

import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.SubscriptionService;

/*
 * Class to support caching as newCacheConfigurationBuilder in the cache constructor apparently
 * does not support complex types (i.e. List<Subscription>).
 * So, we wrap the list inside this class.
 */
class SubscriptionList {

    private List<Subscription> items;

    public SubscriptionList(List<Subscription> items) {
        this.items = items;
    }

    public List<Subscription> getItems() {
        return this.items;
    }
}

@Service
public class CachedSubscriptionService extends SubscriptionService {

    private final Logger logger = LoggerFactory.getLogger(CachedSubscriptionService.class);

    private CacheManager cacheManager;

    private Cache<String, SubscriptionList> subscriptionsCache;

    public CachedSubscriptionService() {
        super();
        this.initCaches();
    }

    private void initCaches() {

        this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
        this.cacheManager.init();

        // size: 200, ttl: 1 hour
        CacheConfiguration<String, SubscriptionList> cconfig = CacheConfigurationBuilder
                .newCacheConfigurationBuilder(String.class, SubscriptionList.class, ResourcePoolsBuilder.heap(10))
                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(30)))
                .build();
        this.subscriptionsCache = this.cacheManager.createCache("subscriptionsCache", cconfig);
        logger.info("Cache 'subscriptionsCache' initialized with size 10 and TTL of 30 minutes");
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
            this.subscriptionsCache.put(key, new SubscriptionList(subscriptions));
        }
        return this.subscriptionsCache.get(key).getItems();
    }

}
