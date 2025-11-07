package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;
import java.util.List;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.SubscriptionService;
import it.eng.dome.revenue.engine.utils.CacheDuration;

@Service
public class CachedSubscriptionService extends SubscriptionService {

    @Value("${caching.revenue.enabled}")
    private Boolean REVENUE_CACHE_ENABLED;

    private final Logger logger = LoggerFactory.getLogger(CachedSubscriptionService.class);

    @Autowired
    CacheService cacheService;
    
	@Autowired
	CacheDuration cacheDuration;


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

@SuppressWarnings("unchecked")
private void initCaches() {
    // Subscription-service caches
    Duration subscriptionsDuration = cacheDuration.getRevenue().get("list-subscription");
    logger.debug("Set cache duration for 'subscriptionsCache' to: {}", subscriptionsDuration);
    subscriptionsCache = cacheService.getOrCreateCache(
            "subscriptionsCache",
            String.class,
            (Class<List<Subscription>>)(Class<?>)List.class,
            subscriptionsDuration
    );

    Duration subscriptionDuration = cacheDuration.getRevenue().get("subscription");
    logger.debug("Set cache duration for 'subscriptionCache' to: {}", subscriptionDuration);
    subscriptionCache = cacheService.getOrCreateCache(
            "subscriptionCache",
            String.class,
            Subscription.class,
            subscriptionDuration
    );
}

    /*
     * Retrieve bills from cache or from the parent class if not cached.
    */
    @Override
    public List<Subscription> getAllSubscriptions() throws ExternalServiceException, BadTmfDataException {
        String key = "all_subscriptions";
        if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);

            List<Subscription> subscriptions = super.getAllSubscriptions();
            if (subscriptions != null && !subscriptions.isEmpty()) {
                this.subscriptionsCache.put(key, subscriptions);
            } else {
                logger.debug("No subscriptions found for {} — not caching null or empty list", key);
            }
        }
        return this.subscriptionsCache.get(key);
    }

    @Override
    public Subscription getSubscriptionByProductId(String productId) throws BadTmfDataException, ExternalServiceException {
    	String key = productId;
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {
			logger.debug("Cache MISS for " + key);

            Subscription subscription = super.getSubscriptionByProductId(productId);
            if (subscription != null) {
                this.subscriptionCache.put(key, subscription);
            } else {
                logger.debug("No subscription found for productId {} — not caching null value", key);
            }
        }
		return this.subscriptionCache.get(key);
    }
    
    @Override
    public Subscription getActiveSubscriptionByRelatedPartyId(String relatedPartyId) throws ExternalServiceException, BadTmfDataException {
		String key = relatedPartyId;
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionCache.containsKey(key)) {
			logger.debug("Cache MISS for " + key);

            Subscription subscription = super.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
            if (subscription != null) {
                this.subscriptionCache.put(key, subscription);
            } else {
                logger.debug("No active subscription found for {} — not caching null value", key);
            }
        }
		return this.subscriptionCache.get(key);
	}

    @Override
    public List<Subscription> getSubscriptionsByRelatedPartyId(String id, Role role) throws ExternalServiceException, BadTmfDataException{
    	String key = id + role.getValue();
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {		
			logger.debug("Cache MISS for " + key);

            List<Subscription> subscriptions = super.getSubscriptionsByRelatedPartyId(id, role);
            if (subscriptions != null && !subscriptions.isEmpty()) {
                this.subscriptionsCache.put(key, subscriptions);
            } else {
                logger.debug("No subscriptions found for {} and role {} — not caching null or empty list", id, role);
            }
        }
		return this.subscriptionsCache.get(key);
	}

}
