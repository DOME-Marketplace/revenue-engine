package it.eng.dome.revenue.engine.service;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.model.SubscriptionPlan;

@Service
public class SubscriptionPlanService {
 
    private final ObjectMapper mapper;


    public SubscriptionPlanService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())        // LocalDate
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
 


    // === GET ALL ===

    
    public List<SubscriptionPlan> loadAllPlans() throws IOException {
        Path dir = Paths.get("src/main/resources/data/plans");
        List<SubscriptionPlan> plans = new ArrayList<>();
        
        if (Files.exists(dir)) {
            return Files.walk(dir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> {
                    try {
                        return mapper.readValue(p.toFile(), SubscriptionPlan.class);
                    } catch (IOException e) {
                        throw new RuntimeException("Error reading file: " + p, e);
                    }
                })
                .collect(Collectors.toList());
        }
        return plans;
    }
    
    // === GET BY ID ===
    public SubscriptionPlan findPlanById(String id) throws IOException {
        List<SubscriptionPlan> plans = loadAllPlans();
        return plans.stream()
            .filter(plan -> plan.getId().equals(id))  
            .findFirst()
            .orElseThrow(() -> new IOException("Plan not found with ID: " + id));
    }
    

    

}