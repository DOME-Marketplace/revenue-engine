package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;
import java.util.List;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.SubscriptionService;

@Service
public class CachedSubscriptionService extends SubscriptionService {

    @Value("${caching.revenue.enabled}")
    private Boolean REVENUE_CACHE_ENABLED;

    private final Logger logger = LoggerFactory.getLogger(CachedSubscriptionService.class);

    @Autowired
    CacheService cacheService;

    private Cache<String, List<Subscription>> subscriptionsCache;

	private Cache<String, Subscription> subscriptionCache;

    public CachedSubscriptionService() {
        super();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.initCaches();
    }

    private void initCaches() {
        this.subscriptionsCache = this.cacheService.getOrCreateCache(
				"subscriptionsCache",
				String.class,
				(Class<List<Subscription>>)(Class<?>)List.class,
				Duration.ofHours(1));
        this.subscriptionCache = this.cacheService.getOrCreateCache(
				"subscriptionCache",
				String.class,
				Subscription.class,
				Duration.ofHours(1));
    }
    

    /*
     * Retrieve bills from cache or from the parent class if not cached.
    */
    @Override
    public List<Subscription> getAllSubscriptions() {
        String key = "all_subscriptions";
        if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            List<Subscription> subscriptions = super.getAllSubscriptions();
            this.subscriptionsCache.put(key, subscriptions);
        }
        return this.subscriptionsCache.get(key);
    }

    @Override
    public Subscription getSubscriptionByProductId(String productId) {
    	String key = productId;
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {
			logger.debug("Cache MISS for " + key);
			Subscription subscription = super.getSubscriptionByProductId(productId);
			this.subscriptionCache.put(key, subscription);
		}
		return this.subscriptionCache.get(key);
    }
    
    @Override
    public Subscription getActiveSubscriptionByRelatedPartyId(String relatedPartyId) {
		String key = relatedPartyId;
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {
			logger.debug("Cache MISS for " + key);
			Subscription subscription = super.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
			this.subscriptionCache.put(key, subscription);
		}
		return this.subscriptionCache.get(key);
	}

    @Override
    public List<Subscription> getSubscriptionsByRelatedPartyId(String id, Role role){
    	String key = id + role.getValue();
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {		
			logger.debug("Cache MISS for " + key);
			List<Subscription> subscriptions = super.getSubscriptionsByRelatedPartyId(id, role);
			this.subscriptionsCache.put(key, subscriptions);
		}
		
		return this.subscriptionsCache.get(key);
	}
			
	
}
