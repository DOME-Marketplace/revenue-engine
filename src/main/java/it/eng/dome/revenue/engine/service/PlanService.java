package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.brokerage.api.ProductOfferingApis;
import it.eng.dome.brokerage.api.ProductOfferingPriceApis;
import it.eng.dome.revenue.engine.model.Plan;
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
    	
    	//TODO: understand how to filter only plan offering
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
    
    public Plan getPlanById(String planId) {

        // FIXME: make this unpack more robust.
        int offeringStart = planId.indexOf("urn:ngsi-ld:product-offering");
        int priceStart = planId.indexOf("urn:ngsi-ld:product-offering-price");
        String offeringId = planId.substring(offeringStart, priceStart);
        String offeringPriceId = planId.substring(priceStart);
        
        return this.findPlan(offeringId, offeringPriceId);
    }

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

    private ProductOfferingPrice fetchProductOfferingPriceById(ProductOffering po, String offeringPriceId) {
    	if (po.getProductOfferingPrice() == null || po.getProductOfferingPrice().isEmpty()) {
            throw new IllegalStateException("ProductOffering has no ProductOfferingPrice");
        }

        for (ProductOfferingPriceRefOrValue ref : po.getProductOfferingPrice()) {
            if (ref.getId().equals(offeringPriceId)) {
                ProductOfferingPrice pop = popApis.getProductOfferingPrice(ref.getId(), null);
                if (pop == null) {
                    throw new IllegalStateException("ProductOfferingPrice not found for id=" + ref.getId());
                }
                return pop;
            }
        }

        throw new IllegalStateException("ProductOfferingPrice id not found: " + offeringPriceId);
    }
 
    public List<Plan> findPlans(String offeringId) {
        if (offeringId == null || offeringId.isEmpty()) {
            throw new IllegalArgumentException("Offering ID cannot be null or empty");
        }
        logger.info("Fetching plans for offering id: {}", offeringId);

        ProductOffering po = productOfferingApis.getProductOffering(offeringId, null);
        if (po == null) {
            throw new IllegalStateException("ProductOffering not found for id=" + offeringId);
        }

        if (po.getProductOfferingPrice() == null || po.getProductOfferingPrice().isEmpty()) {
            logger.error("ProductOffering id={} has no ProductOfferingPrice", offeringId);
            return Collections.emptyList();
        }

        List<Plan> plans = new ArrayList<>();

        for (ProductOfferingPriceRefOrValue popRef : po.getProductOfferingPrice()) {
            ProductOfferingPrice pop = popApis.getProductOfferingPrice(popRef.getId(), null);
            if (pop == null) {
                logger.error("ProductOfferingPrice not found for id={}", popRef.getId());
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
    	plan.setId("urn:ngsi-ld:plan:"+po.getId()+pop.getId());
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
     * Loads all plans from the configured file names.
     * If a plan is already cached (by its plan ID), it is reused.
     * Otherwise, it is loaded from file and added to the cache.
     *
     * @return the list of all loaded Plan objects
     */
//    public List<Plan> loadAllPlans() {
//        logger.info("Loading all plans from {} files", planFileNames.size());
//        
//        List<Plan> cachedPlans = planCache.get("all_plans");
//        if (cachedPlans != null) {
//            logger.info("Retrived all plans from cache");
//            return cachedPlans;
//        }
//        
//        List<Plan> plans = new ArrayList<>();
//        
//
//        for (String fileName : planFileNames) {
//            try {
//                // Load plan from file
//                Plan plan = loadPlanFromUrl(fileName);
//                if (plan == null) {
//                    logger.warn("Plan from file '{}' is null, skipping", fileName);
//                    continue;
//                }
//
//                String planId = plan.getId();
//                if (planId == null || planId.isBlank()) {
//                    logger.warn("Plan loaded from '{}' has no ID, skipping", fileName);
//                    continue;
//                }
//                
//                plans.add(plan);
//                
//              
//            } catch (IOException e) {
//				logger.error("Error while loading plan from file '{}': {}", fileName, e.getMessage());
//				continue;
//			}
//
//        }
//
//        logger.info("Successfully loaded {} plans", plans.size());
//        
//        //store all loaded plans in the cache        
//        planCache.put("all_plans", plans);
//
//        return plans;
//    }
    
    /**
     * Returns the list of JSON plan filenames discovered from GitHub.
     *
     * @return list of JSON file names
     */
//    public List<String> getPlanFileNames() {
//        return planFileNames;
//    }
    
    /**
     * Retrieves a specific plan by its ID. Uses the internal cache if available.
     *
     * @param planId the ID of the plan
     * @return the matching Plan object, or null if not found
     */
//    public Plan findPlanById(String id) throws IOException {
//        if (id == null || id.isEmpty()) {
//            throw new IllegalArgumentException("Plan ID cannot be null or empty");
//        }
//
//        logger.info("Fetching plan with ID {}", id);
//        return loadAllPlans().stream()
//            .filter(plan -> plan.getId().equals(id))
//            .findFirst()
//            .orElseThrow(() -> new IOException("Plan not found with ID: " + id));
//    }
    
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
    
    /**
     * Loads and parses a plan from the raw GitHub URL.
     *
     * @param fileName the file name of the plan (e.g., "basic.json")
     * @return the parsed Plan object, or null if loading fails
     * @throws IOException if the plan cannot be read
     */
//    private Plan loadPlanFromUrl(String fileName) throws IOException {
//        URL planUrl = new URL(PLAN_REPO_RAW_URL + fileName);
//        try (InputStream is = planUrl.openStream()) {
//            Plan plan = mapper.readValue(is, Plan.class);
//            logger.debug("Loaded plan '{}' with ID '{}'", fileName, plan.getId());
//            return plan;
//        }
//    }
    
    /**
     * Fetches the list of available plan filenames from GitHub (only once).
     *
     * @return a list of JSON plan filenames
     */
//    private List<String> fetchPlanFileNamesOnce() {
//        try {
//            URL url = new URL(GITHUB_API_URL);
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestMethod("GET");
//            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
//
//            int status = conn.getResponseCode();
//            if (status == HttpURLConnection.HTTP_OK) {
//                try (InputStream is = conn.getInputStream()) {
//                    JsonNode filesNode = mapper.readTree(is);
//                    List<String> files = new ArrayList<>();
//
//                    for (JsonNode fileNode : filesNode) {
//                        String fileName = fileNode.get("name").asText();
//                        if (fileName.endsWith(".json")) {
//                            files.add(fileName);
//                            logger.debug("Discovered plan file '{}'", fileName);
//                        }
//                    }
//                    
//                    logger.info("Fetched {} plan files from GitHub", files.size());
//                    return files;
//                }
//            } else {
//                logger.error("GitHub API returned status {}", status);
//            }
//        } catch (IOException e) {
//            logger.error("Error fetching plan file list from GitHub", e);
//        }
//
//        return Collections.emptyList();
//    }
}
