package it.eng.dome.revenue.engine.service;

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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

/*
 * ACBR to support caching as newCacheConfigurationBuilder in the cache constructor apparently
 * does not support complex types (i.e. List<AppliedCustomerBillingRate>).
 * So, we wrap the list inside this class.
 */
class ACBRList {

    private List<AppliedCustomerBillingRate> items;

    public ACBRList(List<AppliedCustomerBillingRate> items) {
        this.items = items;
    }

    public List<AppliedCustomerBillingRate> getItems() {
        return this.items;
    }
}

@Component(value = "tmfCachedDataRetriever")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TmfCachedDataRetriever extends TmfDataRetriever {

    private final Logger logger = LoggerFactory.getLogger(TmfCachedDataRetriever.class);

    private CacheManager cacheManager;

    private Cache<String, ACBRList> acbrCache;

    public TmfCachedDataRetriever() {
        super();
        this.initCaches();
    }

    private void initCaches() {

        this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
        this.cacheManager.init();

        // size: 200, ttl: 1 hour
        CacheConfiguration<String, ACBRList> cconfig1 = CacheConfigurationBuilder
                .newCacheConfigurationBuilder(String.class, ACBRList.class, ResourcePoolsBuilder.heap(1000))
                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofHours(1)))
                .build();
        this.acbrCache = this.cacheManager.createCache("acbrCache", cconfig1);
    }

    /*
     * Retrieve bills from cache or from the parent class if not cached.
    */
    @Override
    public List<AppliedCustomerBillingRate> retrieveBills(String sellerId, TimePeriod timePeriod, Boolean isBilled) throws Exception {
        String key = sellerId + timePeriod.toString() + isBilled.toString();
        if (!this.acbrCache.containsKey(key)) {
            logger.debug("Cache MISS for acbr " + key);
            List<AppliedCustomerBillingRate> acbrs = super.retrieveBills(sellerId, timePeriod, isBilled);
            this.acbrCache.put(key, new ACBRList(acbrs));
        }
        return this.acbrCache.get(key).getItems();
    }

}
