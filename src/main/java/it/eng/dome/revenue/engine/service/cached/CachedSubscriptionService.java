package it.eng.dome.revenue.engine.service.cached;

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
    	logger.debug("Set cache duration for 'subscription-service' to: {}", cacheDuration.get("subscription-service"));
        subscriptionsCache = cacheService.getOrCreateCache(
        		"subscriptionsCache", 
        		String.class, 
        		(Class<List<Subscription>>)(Class<?>)List.class, 
        		cacheDuration.get("subscription-service"));
        
        subscriptionCache = cacheService.getOrCreateCache(
        		"subscriptionCache", 
        		String.class, 
        		Subscription.class, 
        		cacheDuration.get("subscription-service"));
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
            this.subscriptionsCache.put(key, subscriptions);
        }
        return this.subscriptionsCache.get(key);
    }

    @Override
    public Subscription getSubscriptionByProductId(String productId) throws BadTmfDataException, ExternalServiceException {
    	String key = productId;
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {
			logger.debug("Cache MISS for " + key);
			Subscription subscription = super.getSubscriptionByProductId(productId);
			this.subscriptionCache.put(key, subscription);
		}
		return this.subscriptionCache.get(key);
    }
    
    @Override
    public Subscription getActiveSubscriptionByRelatedPartyId(String relatedPartyId) throws ExternalServiceException, BadTmfDataException {
		String key = relatedPartyId;
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {
			logger.debug("Cache MISS for " + key);
			Subscription subscription = super.getActiveSubscriptionByRelatedPartyId(relatedPartyId);
			this.subscriptionCache.put(key, subscription);
		}
		return this.subscriptionCache.get(key);
	}

    @Override
    public List<Subscription> getSubscriptionsByRelatedPartyId(String id, Role role) throws ExternalServiceException, BadTmfDataException{
    	String key = id + role.getValue();
		if (!REVENUE_CACHE_ENABLED || !this.subscriptionsCache.containsKey(key)) {		
			logger.debug("Cache MISS for " + key);
			List<Subscription> subscriptions = super.getSubscriptionsByRelatedPartyId(id, role);
			this.subscriptionsCache.put(key, subscriptions);
		}
		
		return this.subscriptionsCache.get(key);
	}
			
	
}
