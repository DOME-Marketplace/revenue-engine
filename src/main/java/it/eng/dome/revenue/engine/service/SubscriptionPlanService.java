package it.eng.dome.revenue.engine.service;


import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.model.SubscriptionPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubscriptionPlanService {
 
    private final ObjectMapper mapper;
    private final Path storageDir = Paths.get("src/main/resources/sample_data/");


    public SubscriptionPlanService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())        // LocalDate
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
 
    // === GET BY FILENAME===

//    public SubscriptionPlan loadFromClasspath(String path) throws Exception {
//        try (var in = new ClassPathResource(path).getInputStream()) {
//            String json = new String(in.readAllBytes());
//            return mapper.readValue(json, SubscriptionPlan.class);
//        }
//    }

    // === GET ALL ===

    
    public List<SubscriptionPlan> loadAllPlans() throws IOException {
        Path dir = Paths.get("src/main/resources/sample_data/");
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
    
    // === CREATE ===

/*    public String saveRawJson(String rawJson, String filename) throws IOException {
        Path dir = Paths.get("src/main/resources/sample_data/");
        Files.createDirectories(dir);
        
        Path filePath = dir.resolve(filename);
        Files.write(filePath, rawJson.getBytes());
        
        return filePath.toString();
    }
    
    // === DELETE ===

    private Path findPlanFile(String planName) throws IOException {
        return Files.walk(storageDir)
            .filter(p -> p.getFileName().toString().equalsIgnoreCase(planName + ".json"))
            .findFirst()
            .orElseThrow(() -> new IOException("Plan not found: " + planName));
    }
    
    public void deletePlan(String planName) throws IOException {
        Path filePath = findPlanFile(planName);
        Files.delete(filePath);
    }
    


    // === UPDATE ===
    
    public String updatePlan(String planName, SubscriptionPlan updatedPlan) throws IOException {
        Path filePath = findPlanFile(planName);
        String json = mapper.writeValueAsString(updatedPlan);
        Files.write(filePath, json.getBytes());
        return filePath.toString();
    }*/
}