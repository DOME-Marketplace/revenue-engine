package it.eng.dome.revenue.engine.service;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic cache service using Ehcache for local caching of any data type.
 * Supports configurable TTL and multiple independent caches.
 */
@Service
public class CacheService {

    private final CacheManager cacheManager;

    // Map to store any typed cache by name
    private final Map<String, Cache<?, ?>> cacheMap = new ConcurrentHashMap<>();

    /**
     * Initializes the internal Ehcache CacheManager.
     */
    public CacheService() {
        this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
    }

    /**
     * Returns an existing or newly created typed cache.
     *
     * @param cacheName   the name of the cache
     * @param keyClass    the class of the key
     * @param valueClass  the class of the value
     * @param ttl         the time-to-live for each entry
     * @param <K>         the type of cache key
     * @param <V>         the type of cache value
     * @return the typed cache instance
     */
    
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getOrCreateCache(String cacheName, Class<K> keyClass, Class<V> valueClass, Duration ttl) {
        return (Cache<K, V>) cacheMap.computeIfAbsent(cacheName, name -> {
            CacheConfiguration<K, V> config = CacheConfigurationBuilder
                    .newCacheConfigurationBuilder(keyClass, valueClass, ResourcePoolsBuilder.heap(500))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(ttl))
                    .build();
            return cacheManager.createCache(name, config);
        });
    }
}
