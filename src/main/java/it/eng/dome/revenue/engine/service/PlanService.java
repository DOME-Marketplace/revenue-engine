package it.eng.dome.revenue.engine.service;


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.model.Plan;

@Service
public class PlanService {
	
	protected final Logger logger = LoggerFactory.getLogger(PlanService.class);
 
    private final ObjectMapper mapper;


    public PlanService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())        // LocalDate
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
 


    /*	 * Loads all plans from JSON files located in the classpath under data/plans/
	 * 
	 * @return List of Plan objects
	 * @throws IOException if there is an error reading the files
	 */

    
    public List<Plan> loadAllPlans() throws IOException {

    	//FIXME - replace to get dynamic contents 
    	logger.debug("Loading all plans from files ... ");
        
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:data/plans/*.json");
        
        List<Plan> plans = new ArrayList<>();
        
        for (Resource resource : resources) {
        	logger.debug("Loading: {} ", resource.getFilename());
            try (InputStream is = resource.getInputStream()) {
                Plan plan = mapper.readValue(is, Plan.class);
                plans.add(plan);
            }
        }
        
        logger.debug("Number of files json read: {}", plans.size());
        return plans;
    }
    
    /**
	 * Finds a plan by its ID.
	 * 
	 * @param id The ID of the plan to find.
	 * @return The Plan object if found.
	 * @throws IOException if there is an error reading the files or if the plan is not found.
	 */
    
    public Plan findPlanById(String id) throws IOException {
    	logger.info("Finding plan by ID: {}", id);
        List<Plan> plans = loadAllPlans();
        return plans.stream()
            .filter(plan -> plan.getId().equals(id))  
            .findFirst()
            .orElseThrow(() -> new IOException("Plan not found with ID: " + id));
    }
    
}