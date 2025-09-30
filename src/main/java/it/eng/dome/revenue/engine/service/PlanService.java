package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.eng.dome.revenue.engine.utils.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.brokerage.api.ProductOfferingApis;
import it.eng.dome.brokerage.api.ProductOfferingPriceApis;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.PlanResolver;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.validation.PlanValidationReport;
import it.eng.dome.revenue.engine.service.validation.PlanValidator;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPriceRefOrValue;

/**
 * Service responsible for loading and caching revenue engine plans defined as external JSON files.
 * Plans are retrieved from a GitHub repository and cached in memory using a shared CacheService.
 */
@Service
public class PlanService implements InitializingBean{
	
	/** Dome Operator ID - now parametric via Spring property */
    @Value("${dome.operator.id}")
    private String DOME_OPERATOR_ID;

    private static final Logger logger = LoggerFactory.getLogger(PlanService.class);
    
    // Factory for TMF APIss
    @Autowired
    private TmfApiFactory tmfApiFactory;
    
    private ProductOfferingApis productOfferingApis;
    
    private ProductOfferingPriceApis popApis;

    private final ObjectMapper mapper;

    /**
     * Constructs the PlanService and initializes the plan cache and file list.
     *
     * @param cacheService shared cache service for creating and retrieving plan cache.
     */
    public PlanService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.productOfferingApis = new ProductOfferingApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient());
        this.popApis = new ProductOfferingPriceApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient()
        );

        logger.info("PlanService initialized with productOfferingApis and productOfferingPriceApis");
    }
    
    // retrieve all plans by offerings
    public List<Plan> getAllPlans() {
    	
    	//FIXME: when the RP bug is fixed, filter only plans connected to DO.
//    	Map<String, String> filter = new HashMap<String, String>();
//    	filter.put("relatedParty.id", DOME_OPERATOR_ID);
        List<ProductOffering> pos = productOfferingApis.getAllProductOfferings(null, null);
        
        List<Plan> plans = new ArrayList<>();
        for (ProductOffering po : pos) {
            List<Plan> planList = findPlans(po.getId());
            if (planList != null && !planList.isEmpty()) {
                plans.addAll(planList);
            }
        }

        logger.info("Loaded {} plans and stored in cache", plans.size());

        return plans;
    }
    /*
     * Retrieves a plan by its ID.
     */
    public Plan getPlanById(String planId) {
        String[] parts = IdUtils.unpack(planId, "plan");
        String offeringId = parts[0];
        String offeringPriceId = parts[1];
        
        return this.findPlan(offeringId, offeringPriceId);
    }

    public Plan getResolvedPlanById(String planId, Subscription sub) {
        PlanResolver planResolver = new PlanResolver(sub);
        Plan plan = this.getPlanById(planId);
        if(plan!=null) {
            return planResolver.resolve(plan);
        } else {
            return null;
        }        
    }

    /*
	 * Retrieves a plan by its offering ID and offering price ID.
	 */
    public Plan findPlan(String offeringId, String offeringPriceId) {
    	if (offeringId == null || offeringId.isEmpty()) {
            throw new IllegalArgumentException("Offering ID cannot be null or empty");
        }
        logger.info("Fetching plan for offering id: {}", offeringId);

        ProductOffering po = productOfferingApis.getProductOffering(offeringId, null);
        if (po == null) {
            throw new IllegalStateException("ProductOffering not found for id=" + offeringId);
        }
        
        ProductOfferingPrice pop = fetchProductOfferingPriceById(po, offeringPriceId);
        String link = extractLinkFromDescription(pop.getDescription());

        try {
            Plan plan = this.loadPlanFromLink(link);
            return this.overwritingPlanByProductOffering(plan, po, pop);
		} catch (IOException e) {
			logger.error("Failed to load Plan from link={}", link, e);
            return null;
		}
    }

    /*
     * Fetches the ProductOfferingPrice by its ID from the given ProductOffering.
     */
    private ProductOfferingPrice fetchProductOfferingPriceById(ProductOffering po, String offeringPriceId) {
    	if (po.getProductOfferingPrice() == null || po.getProductOfferingPrice().isEmpty()) {
            //throw new IllegalStateException("ProductOffering has no ProductOfferingPrice");
        }

        for (ProductOfferingPriceRefOrValue ref : po.getProductOfferingPrice()) {
            if (ref.getId().equals(offeringPriceId)) {
                ProductOfferingPrice pop = popApis.getProductOfferingPrice(ref.getId(), null);
                if (pop == null) {
                    //throw new IllegalStateException("ProductOfferingPrice not found for id=" + ref.getId());
                }
                return pop;
            }
        }

        throw new IllegalStateException("ProductOfferingPrice id not found: " + offeringPriceId);
    }
    
    /*
	 * Retrieves all plans associated with the given offering ID.
	 */
 
    public List<Plan> findPlans(String offeringId) {
        if (offeringId == null || offeringId.isEmpty()) {
            throw new IllegalArgumentException("Offering ID cannot be null or empty");
        }
        logger.info("Fetching plans for offering id: {}", offeringId);

        ProductOffering po = productOfferingApis.getProductOffering(offeringId, null);
        if (po == null) {
            //throw new IllegalStateException("ProductOffering not found for id=" + offeringId);
        }

        if (po.getProductOfferingPrice() == null || po.getProductOfferingPrice().isEmpty()) {
            //logger.error("ProductOffering id={} has no ProductOfferingPrice", offeringId);
            return Collections.emptyList();
        }

        List<Plan> plans = new ArrayList<>();

        for (ProductOfferingPriceRefOrValue popRef : po.getProductOfferingPrice()) {
            ProductOfferingPrice pop = popApis.getProductOfferingPrice(popRef.getId(), null);
            if (pop == null) {
                //logger.error("ProductOfferingPrice not found for id={}", popRef.getId());
                continue; //
            }

            try {
                String link = extractLinkFromDescription(pop.getDescription());
                Plan plan;
                try {
                    plan = loadPlanFromLink(link);
                } catch (IOException e) {
                    logger.error("Failed to load Plan from link={}", link, e);
                    continue;
                }
                
                plan = this.overwritingPlanByProductOffering(plan, po, pop);

                plans.add(plan);
                logger.info("Plan loaded for offeringId={}, priceId={}", offeringId, pop.getId());
            } catch (IllegalStateException e) {
                logger.error("Skipping ProductOfferingPrice id={} due to error: {}", popRef.getId(), e.getMessage());
            }
        }

        return plans;
    }
    
    private String extractLinkFromDescription(String description) {
        if (description == null || description.isEmpty()) {
            throw new IllegalStateException("Description is null or empty");
        }

        Pattern pattern = Pattern.compile("https?://\\S+");
        Matcher matcher = pattern.matcher(description);
        if (matcher.find()) {
            return matcher.group();
        }

        throw new IllegalStateException("No link found in description");
    }

    private Plan loadPlanFromLink(String link) throws IOException {
        URL planUrl = new URL(link);
        try (InputStream is = planUrl.openStream()) {
            Plan plan = mapper.readValue(is, Plan.class);
            logger.debug("Loaded plan '{}' with ID '{}'", link, plan.getId());
            return plan;
        }
    }

    private Plan overwritingPlanByProductOffering(Plan plan, ProductOffering po, ProductOfferingPrice pop) {
    	plan.setId(plan.generateId(po.getId(), pop.getId()));
        plan.setLifecycleStatus(po.getLifecycleStatus());
        plan.setDescription(po.getDescription());
        
        return plan;
    }
    
    /**
     * Validates the plan corresponding to the offering ID.
     *
     * @param offeringId the ID of the offering
     * @return a PlanValidationReport with validation results
     */
    public PlanValidationReport validatePlan(String planId) throws IOException {
        Plan plan = getPlanById(planId);
        return new PlanValidator().validate(plan);
    }
    
    /**
     * Validates the plan corresponding to the given ID.
     *
     * @param planId the ID of the plan
     * @return a PlanValidationReport with validation results
     */
//    public PlanValidationReport validatePlan(String planId) throws IOException {
//        Plan plan = findPlanById(planId);
//        return new PlanValidator().validate(plan);
//    }
}
