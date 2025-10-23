package it.eng.dome.revenue.engine.service.cached;

import java.util.List;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.exception.BadRevenuePlanException;
import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Report;
import it.eng.dome.revenue.engine.service.ReportingService;
import it.eng.dome.revenue.engine.utils.CacheDuration;

@Service
public class CachedReportingService extends ReportingService {

    @Value("${caching.revenue.enabled}")
    private Boolean REVENUE_CACHE_ENABLED;

    private final Logger logger = LoggerFactory.getLogger(CachedReportingService.class);

    @Autowired
    private CacheService cacheService;
    
	@Autowired
	CacheDuration cacheDuration;

    private Cache<String, List<Report>> reportCache;

    public CachedReportingService() {
        super();
    }

    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        this.initCaches();
    }

    @SuppressWarnings("unchecked")
	private void initCaches() {
		logger.debug("Set cache duration for 'reporting-service' to: {}", cacheDuration.get("reporting-service"));
		reportCache = cacheService.getOrCreateCache(
				"reportCache", 
				String.class, 
				(Class<List<Report>>)(Class<?>)List.class, 
				cacheDuration.get("reporting-service"));
    }

    @Override
    public List<Report> getDashboardReport(String partyId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException  {
        String key = partyId;
        if (!REVENUE_CACHE_ENABLED || !this.reportCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            List<Report> plans = super.getDashboardReport(partyId);
            this.reportCache.put(key, plans);
        }
        return this.reportCache.get(key);
    }

}
