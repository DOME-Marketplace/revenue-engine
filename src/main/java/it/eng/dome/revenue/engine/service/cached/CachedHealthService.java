package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.observability.health.Health;
import it.eng.dome.revenue.engine.service.HealthService;

@Service
public class CachedHealthService extends HealthService {

    private final Logger logger = LoggerFactory.getLogger(CachedHealthService.class);

    @Value("${caching.health.enabled}")
    private Boolean HEALTH_CACHE_ENABLED;

    @Autowired
    CacheService cacheService;

    private Cache<String, Health> healthCache;

    public CachedHealthService() {
        super();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.initCaches();
    }

    private void initCaches() {
        this.healthCache = this.cacheService.getOrCreateCache(
				"healthCache",
				String.class,
				Health.class,
				Duration.ofMinutes(1));
    }

    @Override
    public Health getHealth() {
        String key = "health";
        if (!HEALTH_CACHE_ENABLED || !this.healthCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            Health h = super.getHealth();
            this.healthCache.put(key, h);
        } 
        return this.healthCache.get(key);

    }

}
