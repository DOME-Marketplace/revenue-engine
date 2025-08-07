package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.mapper.RevenueProductMapper;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.service.validation.PlanValidationReport;
import it.eng.dome.revenue.engine.service.validation.PlanValidator;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;

@Service
public class PlanService {
    private static final Logger logger = LoggerFactory.getLogger(PlanService.class);
    
    private static final String GITHUB_API_URL = "https://api.github.com/repos/DOME-Marketplace/revenue-engine/contents/src/main/resources/data/plans?ref=develop";
    private static final String PLAN_REPO_RAW_URL = "https://raw.githubusercontent.com/DOME-Marketplace/revenue-engine/develop/src/main/resources/data/plans/";

    private final ObjectMapper mapper;
    private final List<String> planFileNames;

    // CACHE:
    private final CacheManager cacheManager;
    private final Cache<String, Plan> planCache;

    /**
     * Constructs the PlanService, initializing the ObjectMapper, loading the list of plan file names,
     * and setting up the EHCache for plan caching.
     */
    public PlanService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.planFileNames = Collections.unmodifiableList(fetchPlanFileNamesOnce());
        logger.info("Initialized PlanService with {} plan files", planFileNames.size());

        // CACHE: init
        this.cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
        CacheConfiguration<String, Plan> cacheConfig = CacheConfigurationBuilder
                .newCacheConfigurationBuilder(String.class, Plan.class, ResourcePoolsBuilder.heap(500))
                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(java.time.Duration.ofHours(1)))
                .build();
        this.planCache = cacheManager.createCache("planCache", cacheConfig);
        logger.info("Initialized EHCache for PlanService with TTL 1 hour");
    }

    /**
     * Fetches the list of plan file names once from the GitHub repository using the GitHub API.
     *
     * @return an unmodifiable list of plan file names
     */
    private List<String> fetchPlanFileNamesOnce() {
        try {
            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (InputStream is = conn.getInputStream()) {
                    JsonNode filesNode = mapper.readTree(is);
                    List<String> files = new ArrayList<>();

                    for (JsonNode fileNode : filesNode) {
                        String fileName = fileNode.get("name").asText();
                        if (fileName.endsWith(".json")) {
                            files.add(fileName);
                            logger.debug("Found plan file: {}", fileName);
                        }
                    }
                    logger.info("Fetched {} plan files from GitHub", files.size());
                    return files;
                }
            } else {
                logger.error("GitHub API responded with status: {}", conn.getResponseCode());
            }
        } catch (IOException e) {
            logger.error("Failed to fetch plan files from GitHub", e);
        }
        return Collections.emptyList();
    }

    /**
     * Loads all available plans from the remote repository.
     *
     * @return a list of all successfully loaded Plan objects
     */
    public List<Plan> loadAllPlans() {
        logger.info("Loading all plans from {} files", planFileNames.size());
        List<Plan> plans = new ArrayList<>();

        for (String fileName : planFileNames) {
            try {
                Plan plan = loadPlanFromUrl(fileName);
                if (plan != null) {
                    plans.add(plan);
                }
            } catch (Exception e) {
                logger.error("Failed to load plan from {}", fileName, e);
            }
        }

        logger.info("Successfully loaded {} plans", plans.size());
        return plans;
    }

    /**
     * Finds a plan by its unique ID. Uses cache if available.
     *
     * @param planId the ID of the plan to search for
     * @return the matching Plan, or null if not found
     */
    public Plan findPlanById(String planId) {
        if (planId == null || planId.isBlank()) {
            logger.error("Plan ID cannot be null or empty");
            return null;
        }

        // CACHE: check first
        if (planCache.containsKey(planId)) {
            logger.info("Found cached plan with ID: {}", planId);
            return planCache.get(planId);
        }

        for (String fileName : planFileNames) {
            try {
                Plan plan = loadPlanFromUrl(fileName);
                if (plan != null && planId.equals(plan.getId())) {
                    // CACHE: store result
                    planCache.put(planId, plan);
                    return plan;
                }
            } catch (Exception e) {
                logger.error("Error while searching for plan {} in file {}", planId, fileName, e);
            }
        }

        logger.error("Plan with ID {} not found", planId);
        return null;
    }

    /**
     * Loads a plan JSON file from the GitHub raw URL and parses it into a Plan object.
     *
     * @param fileName the name of the plan JSON file
     * @return the parsed Plan object
     * @throws IOException if an error occurs while loading or parsing the file
     */
    private Plan loadPlanFromUrl(String fileName) throws IOException {
        URL planUrl = new URL(PLAN_REPO_RAW_URL + fileName);
        try (InputStream is = planUrl.openStream()) {
            Plan plan = mapper.readValue(is, Plan.class);
            logger.debug("Loaded plan {}: {}", fileName, plan.getId());
            return plan;
        }
    }

    /**
     * Returns the list of available plan file names.
     *
     * @return an unmodifiable list of plan file names
     */
    public List<String> getPlanFileNames() {
        return planFileNames;
    }

    /**
     * Validates a plan identified by its ID.
     *
     * @param planId the ID of the plan to validate
     * @return a PlanValidationReport containing validation results
     */
    public PlanValidationReport validatePlan(String planId) {
        Plan plan = findPlanById(planId);
        return new PlanValidator().validate(plan);
    }

    /**
     * Converts a Plan into a TMForum-compliant ProductOffering.
     *
     * @param plan the Plan to convert
     * @return the corresponding ProductOffering
     */
    public ProductOffering buildProductOffering(Plan plan) {
        return RevenueProductMapper.fromPlanToProductOffering(plan);
    }
}
