package it.eng.dome.revenue.engine.service.cached;

import java.time.Duration;
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
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.service.PlanService;

@Service
public class CachedPlanService extends PlanService {

    private final Logger logger = LoggerFactory.getLogger(CachedPlanService.class);

    @Value("${caching.revenue.enabled}")
    private Boolean REVENUE_CACHE_ENABLED;

    @Autowired
    private CacheService cacheService;

    private Cache<String, List<Plan>> planSetCache;
    private Cache<String, Plan> planCache;

    public CachedPlanService() {
        super();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.initCaches();
    }

    private void initCaches() {
        this.planSetCache = this.cacheService.getOrCreateCache(
				"planSetCache",
				String.class,
				(Class<List<Plan>>)(Class<?>)List.class,
				Duration.ofHours(1));
        this.planCache = this.cacheService.getOrCreateCache(
				"planCache",
				String.class,
				Plan.class,
				Duration.ofHours(1));
    }

    /*
     * Retrieve bills from cache or from the parent class if not cached.
    */
    @Override
    public List<Plan> getAllPlans() throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        String key = "all-plans";
        if (!REVENUE_CACHE_ENABLED || !this.planSetCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            List<Plan> plans = super.getAllPlans();
            this.planSetCache.put(key, plans);
            logger.info("Caching {} plans", plans.size());
        }
        return this.planSetCache.get(key);
    }

    @Override
    public Plan getPlanById(String planId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        String key = planId;
        if (!REVENUE_CACHE_ENABLED || !this.planCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            Plan plan = super.getPlanById(planId);
            this.planCache.put(key, plan);
        } 
        return this.planCache.get(key);
    }

    @Override
    public Plan findPlan(String offeringId, String offeringPriceId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        String key = offeringId+offeringPriceId;
        if (!REVENUE_CACHE_ENABLED || !this.planCache.containsKey(key)) {
            logger.debug("Cache MISS for " + key);
            Plan plan = super.findPlan(offeringId, offeringPriceId);
            this.planCache.put(key, plan);
        } 
        return this.planCache.get(key);
    }
}
