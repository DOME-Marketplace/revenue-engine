package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.brokerage.api.ProductOfferingApis;
import it.eng.dome.brokerage.api.ProductOfferingPriceApis;
import it.eng.dome.revenue.engine.mapper.RevenueProductMapper;
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
    
    @Autowired
    // Factory for TMF APIss
    private TmfApiFactory tmfApiFactory;
    
    private ProductOfferingApis productOfferingApis;
    
    private ProductOfferingPriceApis popApis;

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/DOME-Marketplace/revenue-engine/contents/src/main/resources/data/plans?ref=develop";

    private static final String PLAN_REPO_RAW_URL =
            "https://raw.githubusercontent.com/DOME-Marketplace/revenue-engine/develop/src/main/resources/data/plans/";

    private final ObjectMapper mapper;
    private final List<String> planFileNames;
    private final Cache<String, List<Plan>> planCache;

    /**
     * Constructs the PlanService and initializes the plan cache and file list.
     *
     * @param cacheService shared cache service for creating and retrieving plan cache.
     */
    public PlanService(CacheService cacheService) {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        

        this.planFileNames = Collections.unmodifiableList(fetchPlanFileNamesOnce());
        this.planCache = cacheService.getOrCreateCache(
				"planCache",
				String.class,
				(Class<List<Plan>>)(Class<?>)List.class,
				Duration.ofHours(1)
		);

        logger.info("Initialized PlanService with {} plan files and cache TTL of 1 hour", planFileNames.size());
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.productOfferingApis = new ProductOfferingApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient());
        this.popApis = new ProductOfferingPriceApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient()
        );

        logger.info("PlanService initialized with productOfferingApis and productOfferingPriceApis");
    }

    /**
     * Loads all plans from the configured file names.
     * If a plan is already cached (by its plan ID), it is reused.
     * Otherwise, it is loaded from file and added to the cache.
     *
     * @return the list of all loaded Plan objects
     */
    public List<Plan> loadAllPlans() {
        logger.info("Loading all plans from {} files", planFileNames.size());
        
        List<Plan> cachedPlans = planCache.get("all_plans");
        if (cachedPlans != null) {
            logger.info("Retrived all plans from cache");
            return cachedPlans;
        }
        
        List<Plan> plans = new ArrayList<>();
        

        for (String fileName : planFileNames) {
            try {
                // Load plan from file
                Plan plan = loadPlanFromUrl(fileName);
                if (plan == null) {
                    logger.warn("Plan from file '{}' is null, skipping", fileName);
                    continue;
                }

                String planId = plan.getId();
                if (planId == null || planId.isBlank()) {
                    logger.warn("Plan loaded from '{}' has no ID, skipping", fileName);
                    continue;
                }
                
                plans.add(plan);
                
              
            } catch (IOException e) {
				logger.error("Error while loading plan from file '{}': {}", fileName, e.getMessage());
				continue;
			}

        }

        logger.info("Successfully loaded {} plans", plans.size());
        
        //store all loaded plans in the cache        
        planCache.put("all_plans", plans);

        return plans;
    }

    /**
     * Retrieves a specific plan by its ID. Uses the internal cache if available.
     *
     * @param planId the ID of the plan
     * @return the matching Plan object, or null if not found
     */
    public Plan findPlanById(String id) throws IOException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Plan ID cannot be null or empty");
        }

        logger.info("Fetching plan with ID {}", id);
        return loadAllPlans().stream()
            .filter(plan -> plan.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new IOException("Plan not found with ID: " + id));
    }

    public Plan findPlanByProductId(String offeringId) throws IOException {
    	return null;
    }
    
    public Plan findPlanByOfferingId(String offeringId) throws IOException {
        if (offeringId == null || offeringId.isEmpty()) {
            throw new IllegalArgumentException("Offering ID cannot be null or empty");
        }

        logger.info("Fetching plan from offering id: {}", offeringId);
        // TODO: retrieve offering, retrieve price, read description, download json from github, build a "Plan" object.
        
        // ProductOffering
        ProductOffering po = productOfferingApis.getProductOffering(offeringId, null);
        
        // ProductOfferingPrice
        if (po.getProductOfferingPrice() == null || po.getProductOfferingPrice().isEmpty()) {
            logger.error("ProductOffering id={} has no ProductOfferingPrice", offeringId);
            return null;
        }

        ProductOfferingPriceRefOrValue popRef = po.getProductOfferingPrice().get(0); 
        
        if (popRef == null) {
            logger.error("No ProductOfferingPrice named 'Plan' found in offering id={}", offeringId);
            return null;
        }
        if (popRef.getId() == null || popRef.getId().isEmpty()) {
            logger.error("ProductOfferingPriceRefOrValue has null/empty id in offering id={}", offeringId);
            return null;
        }
        
        ProductOfferingPrice pop = popApis.getProductOfferingPrice(popRef.getId(), null);
        if (pop == null) {
            logger.error("ProductOfferingPrice not found for id={}", popRef.getId());
            return null;
        }
        logger.info("Fetched ProductOfferingPrice with id: {}", pop.getId());
        

        // Description for plan link
        String description = pop.getDescription();
        if (description == null || description.isEmpty()) {
            logger.error("ProductOfferingPrice id={} has no description", popRef.getId());
            return null;
        }
        
        String link = null;
        Pattern pattern = Pattern.compile("https?://\\S+");
        Matcher matcher = pattern.matcher(description);
        if (matcher.find()) {
            link = matcher.group();
        }
        if (link == null || link.isEmpty()) {
            logger.error("No link found in description of ProductOfferingPrice id={}", popRef.getId());
            return null;
        }
        logger.info("Plan link from description: {}", link);

        // Plan
        Plan plan = this.loadPlanFromLink(link);
        if (plan == null) {
            logger.error("Failed to load Plan from link={}", link);
            return null;
        }

        logger.info("Plan loaded for offeringId: {}", offeringId);
        return plan;
    }

    /**
     * Returns the list of JSON plan filenames discovered from GitHub.
     *
     * @return list of JSON file names
     */
    public List<String> getPlanFileNames() {
        return planFileNames;
    }

    /**
     * Validates the plan corresponding to the given ID.
     *
     * @param planId the ID of the plan
     * @return a PlanValidationReport with validation results
     */
    public PlanValidationReport validatePlan(String planId) throws IOException {
        Plan plan = findPlanById(planId);
        return new PlanValidator().validate(plan);
    }

    /**
     * Converts a Plan into a TMForum ProductOffering structure.
     *
     * @param plan the Plan object
     * @return a TMF-compliant ProductOffering
     */
    public ProductOffering buildProductOffering(Plan plan) {
        return RevenueProductMapper.fromPlanToProductOffering(plan);
    }

    /**
     * Loads and parses a plan from the raw GitHub URL.
     *
     * @param fileName the file name of the plan (e.g., "basic.json")
     * @return the parsed Plan object, or null if loading fails
     * @throws IOException if the plan cannot be read
     */
    private Plan loadPlanFromUrl(String fileName) throws IOException {
        URL planUrl = new URL(PLAN_REPO_RAW_URL + fileName);
        try (InputStream is = planUrl.openStream()) {
            Plan plan = mapper.readValue(is, Plan.class);
            logger.debug("Loaded plan '{}' with ID '{}'", fileName, plan.getId());
            return plan;
        }
    }
    
    private Plan loadPlanFromLink(String link) throws IOException {
        URL planUrl = new URL(link);
        try (InputStream is = planUrl.openStream()) {
            Plan plan = mapper.readValue(is, Plan.class);
            logger.debug("Loaded plan '{}' with ID '{}'", link, plan.getId());
            return plan;
        }
    }

    /**
     * Fetches the list of available plan filenames from GitHub (only once).
     *
     * @return a list of JSON plan filenames
     */
    private List<String> fetchPlanFileNamesOnce() {
        try {
            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream()) {
                    JsonNode filesNode = mapper.readTree(is);
                    List<String> files = new ArrayList<>();

                    for (JsonNode fileNode : filesNode) {
                        String fileName = fileNode.get("name").asText();
                        if (fileName.endsWith(".json")) {
                            files.add(fileName);
                            logger.debug("Discovered plan file '{}'", fileName);
                        }
                    }
                    
                    logger.info("Fetched {} plan files from GitHub", files.size());
                    return files;
                }
            } else {
                logger.error("GitHub API returned status {}", status);
            }
        } catch (IOException e) {
            logger.error("Error fetching plan file list from GitHub", e);
        }

        return Collections.emptyList();
    }
}
