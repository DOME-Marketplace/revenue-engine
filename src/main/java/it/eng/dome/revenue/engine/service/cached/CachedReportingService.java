package it.eng.dome.revenue.engine.service.cached;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.model.Report;
import it.eng.dome.revenue.engine.service.ReportingService;
import it.eng.dome.tmforum.tmf632.v4.ApiException;

@Service
public class CachedReportingService extends ReportingService {

    private final Logger logger = LoggerFactory.getLogger(CachedReportingService.class);

    @Autowired
    CacheService cacheService;

    private Cache<String, List<Report>> reportCache;

    public CachedReportingService() {
        super();
    }

    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        this.initCaches();
    }

    private void initCaches() {
        this.reportCache = this.cacheService.getOrCreateCache(
				"reportCache",
				String.class,
				(Class<List<Report>>)(Class<?>)List.class,
				Duration.ofHours(1));
    }

    @Override
    public List<Report> getDashboardReport(String partyId) throws ApiException, IOException {
        String key = partyId;
        if (!this.reportCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            List<Report> plans = super.getDashboardReport(partyId);
            this.reportCache.put(key, plans);
        }
        return this.reportCache.get(key);
    }

}
