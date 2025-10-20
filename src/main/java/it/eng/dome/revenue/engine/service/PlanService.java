package it.eng.dome.revenue.engine.service;

import java.io.File;
import java.io.FileInputStream;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.exception.BadRevenuePlanException;
import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.PlanResolver;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.service.validation.PlanValidationReport;
import it.eng.dome.revenue.engine.service.validation.PlanValidator;
import it.eng.dome.revenue.engine.utils.IdUtils;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPrice;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingPriceRefOrValue;

/**
 * Service responsible for loading and caching revenue engine plans defined as external JSON files.
 * Plans are retrieved from a GitHub repository and cached in memory using a shared CacheService.
 */
@Service
public class PlanService implements InitializingBean {

    /** Dome Operator ID - now parametric via Spring property */
    @Value("${dome.operator.id}")
    private String DOME_OPERATOR_ID;

    @Value("${dev.use-local-plans}")
    private Boolean DEV_USE_LOCAL_PLANS;

    private static final Logger logger = LoggerFactory.getLogger(PlanService.class);

    @Autowired
    private TmfCachedDataRetriever tmfDataRetriever;

    private final ObjectMapper mapper;

    public void afterPropertiesSet() throws Exception {}

    /**
     * Constructs the PlanService and initializes the plan cache and file list.
     */
    public PlanService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // retrieve all plans by offerings
    public List<Plan> getAllPlans() throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        logger.info("Fetching all plans...");

        List<ProductOffering> pos = tmfDataRetriever.getAllSubscriptionProductOfferings();
        if (pos == null) 
			return Collections.emptyList();

        List<Plan> plans = new ArrayList<>();
        for (ProductOffering po : pos) {
            plans.addAll(findPlans(po.getId()));
        }

        logger.info("Total plans fetched: {}", plans.size());
        return plans;
    }

    /*
     * Retrieves a plan by its ID.
     */
    public Plan getPlanById(String planId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        String[] parts = IdUtils.unpack(planId, "plan");
        String offeringId = parts[0];
        String offeringPriceId = parts[1];
        return this.findPlan(offeringId, offeringPriceId);
    }

    public Plan getResolvedPlanById(String planId, Subscription sub) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        PlanResolver planResolver = new PlanResolver(sub);
        Plan plan = this.getPlanById(planId);
        return plan != null ? planResolver.resolve(plan) : null;
    }

    /*
     * Retrieves a plan by its offering ID and offering price ID.
     */
    public Plan findPlan(String offeringId, String offeringPriceId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        if (offeringId == null || offeringId.isEmpty()) {
            throw new BadTmfDataException("ProductOffering", offeringId, "Offering ID cannot be null or empty");
        }

        ProductOffering po = tmfDataRetriever.getProductOfferingById(offeringId, null);
        if (po == null) {
            throw new BadTmfDataException("ProductOffering", offeringId, "ProductOffering not found");
        }

        ProductOfferingPrice pop = fetchProductOfferingPriceById(po, offeringPriceId);
        String link = extractLinkFromDescription(pop.getDescription());

        try {
            Plan plan = this.loadPlanFromLink(link);
            if (plan == null) throw new BadRevenuePlanException(new Plan(), "Plan could not be loaded from link: " + link);
            this.overwritingPlanByProductOffering(plan, po, pop);
            return plan;
        } catch (IOException e) {
            throw new BadRevenuePlanException(new Plan(), "Failed to load Plan from link=" + link);
        }
    }

    /*
     * Fetches the ProductOfferingPrice by its ID from the given ProductOffering.
     */
    private ProductOfferingPrice fetchProductOfferingPriceById(ProductOffering po, String offeringPriceId) throws BadTmfDataException, ExternalServiceException {
        if (po.getProductOfferingPrice() == null || po.getProductOfferingPrice().isEmpty()) {
            throw new BadTmfDataException("ProductOfferingPrice", offeringPriceId, "ProductOffering has no ProductOfferingPrice");
        }

        for (ProductOfferingPriceRefOrValue ref : po.getProductOfferingPrice()) {
            if (ref.getId().equals(offeringPriceId)) {
                ProductOfferingPrice price = tmfDataRetriever.getProductOfferingPrice(ref.getId(), null);
                if (price == null)
                    throw new BadTmfDataException("ProductOfferingPrice", offeringPriceId, "Referenced price not found");
                return price;
            }
        }

        throw new BadTmfDataException("ProductOfferingPrice", offeringPriceId, "Price not found in ProductOffering");
    }

    /*
     * Retrieves all plans associated with the given offering ID.
     */
    public List<Plan> findPlans(String offeringId) throws BadTmfDataException, BadRevenuePlanException, ExternalServiceException {
        if (offeringId == null || offeringId.isEmpty()) {
            throw new BadTmfDataException("ProductOffering", offeringId, "Offering ID cannot be null or empty");
        }

        ProductOffering po = tmfDataRetriever.getProductOfferingById(offeringId, null);
        if (po == null) {
            throw new BadTmfDataException("ProductOffering", offeringId, "ProductOffering not found");
        }

        if (po.getProductOfferingPrice() == null || po.getProductOfferingPrice().isEmpty()) {
            return Collections.emptyList(); 
        }

        List<Plan> plans = new ArrayList<>();
        for (ProductOfferingPriceRefOrValue popRef : po.getProductOfferingPrice()) {
            ProductOfferingPrice pop = tmfDataRetriever.getProductOfferingPrice(popRef.getId(), null);
            if (pop == null) throw new BadTmfDataException("ProductOfferingPrice", popRef.getId(), "Referenced price not found"); 

            try {
                String link = this.extractLinkFromDescription(pop.getDescription());
                Plan plan = loadPlanFromLink(link);
                if (plan == null) throw new BadRevenuePlanException(new Plan(), "Plan not found at link: " + link); 
                this.overwritingPlanByProductOffering(plan, po, pop);
                plans.add(plan);
            } catch (IOException e) {
                throw new BadRevenuePlanException(new Plan(), "Failed to load Plan from link in ProductOfferingPrice id=" + popRef.getId());
            }
        }

        return plans;
    }

    private String extractLinkFromDescription(String description) throws BadRevenuePlanException {
        if (description == null || description.isEmpty()) {
            throw new BadRevenuePlanException(new Plan(), "Description is null or empty");
        }
        Pattern pattern = Pattern.compile("https?://raw\\.githubusercontent\\.com\\S+");
        Matcher matcher = pattern.matcher(description);
        if (matcher.find()) return matcher.group();

        throw new BadRevenuePlanException(new Plan(), "No link found in description");
    }

    private Plan loadPlanFromLink(String link) throws IOException, BadRevenuePlanException, ExternalServiceException {
        if(DEV_USE_LOCAL_PLANS) {
            // get the last part of the link
            String[] parts = link.split("/");
            String planFileName = parts[parts.length-1];
            // search it within src/main/resources/data/plans
            Plan plan = this.loadPlanFromFile("./src/main/resources/data/plans/"+planFileName);
            if (plan == null) throw new BadRevenuePlanException(new Plan(), "Plan not found in file: " + planFileName);
            return plan;
        } else {
            try {
                URL planUrl = new URL(link);
                try (InputStream is = planUrl.openStream()) {
                    Plan plan = mapper.readValue(is, Plan.class);
                    if (plan == null) throw new BadRevenuePlanException(new Plan(), "Plan not found at URL: " + link);
                    logger.debug("Loaded plan '{}' with ID '{}'", link, plan.getId());
                    return plan;
                }
            } catch (IOException e) {
                throw new ExternalServiceException("Failed to retrieve plan from external URL: " + link);
            }
        }
    }

    protected Plan loadPlanFromFile(String path) throws IOException, BadRevenuePlanException {
        File file = new File(path);
        try (InputStream is = new FileInputStream(file)) {
            Plan plan = mapper.readValue(is, Plan.class);
            if (plan == null) throw new BadRevenuePlanException(new Plan(), "Plan not found in file: " + path); 
            return plan;
        } catch(IOException e) {
            throw new BadRevenuePlanException(new Plan(), "Failed to load plan from file: " + path);
        }
    }

    private void overwritingPlanByProductOffering(Plan plan, ProductOffering po, ProductOfferingPrice pop) throws BadRevenuePlanException {
        plan.setId(plan.generateId(po.getId(), pop.getId()));
        plan.setLifecycleStatus(po.getLifecycleStatus());
        plan.setDescription(po.getDescription());
    }

    /**
     * Validates the plan corresponding to the offering ID.
     *
     * @return a PlanValidationReport with validation results
     */
    public PlanValidationReport validatePlan(String planId) throws BadRevenuePlanException, BadTmfDataException, ExternalServiceException {
        Plan plan = getPlanById(planId);
        return new PlanValidator().validate(plan);
    }

    //TODO: to be removed when not needed anymore
    public PlanValidationReport validatePlanTest(Plan plan) throws IOException, BadRevenuePlanException {
        return new PlanValidator().validate(plan);
    }
}
