package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.APIPartyApis;
import it.eng.dome.brokerage.api.AgreementManagementApis;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerManagementApis;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;
import it.eng.dome.brokerage.observability.health.Health;
import it.eng.dome.revenue.engine.service.HealthService;
import it.eng.dome.revenue.engine.utils.CacheDuration;
import jakarta.annotation.PostConstruct;

@Service
public class CachedHealthService extends HealthService {

	public CachedHealthService(ProductCatalogManagementApis productCatalogManagementApis,
			CustomerManagementApis customerManagementApis, APIPartyApis apiPartyApis,
			ProductInventoryApis productInventoryApis, AgreementManagementApis agreementManagementApis,
			AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
		
		super(productCatalogManagementApis, customerManagementApis, apiPartyApis, productInventoryApis,
				agreementManagementApis, appliedCustomerBillRateApis);
	}

	private final Logger logger = LoggerFactory.getLogger(CachedHealthService.class);

	@Value("${caching.health.enabled}")
	private Boolean HEALTH_CACHE_ENABLED;

	@Autowired
	CacheService cacheService;
	
	@Autowired
	CacheDuration cacheDuration;

	private Cache<String, Health> healthCache;

	@PostConstruct
	private void initCaches() {
	    // Health cache
	    Duration healthDuration = cacheDuration.getHealth().getDuration();
	    logger.debug("Set cache duration for 'healthCache' to: {}", healthDuration);
	    healthCache = cacheService.getOrCreateCache(
	            "healthCache",
	            String.class,
	            Health.class,
	            healthDuration
	    );
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