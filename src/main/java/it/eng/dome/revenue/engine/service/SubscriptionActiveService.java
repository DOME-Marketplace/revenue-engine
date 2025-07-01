package it.eng.dome.revenue.engine.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.model.SubscriptionActive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubscriptionActiveService {

    private final Path storageDir = Paths.get("src/main/resources/sample_data/sub/");

    private final ObjectMapper mapper;

    public SubscriptionActiveService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // === GET BY FILENAME===

//    public SubscriptionActive loadFromClasspath(String path) throws IOException {
//        try (var in = new ClassPathResource(path).getInputStream()) {
//            return mapper.readValue(in, SubscriptionActive.class);
//        }
//    }

    // === GET ALL  ===

    public List<SubscriptionActive> loadAllFromStorage() throws IOException {
        
        List<SubscriptionActive> subscriptions = new ArrayList<>();

        if (Files.exists(storageDir)) {
            return Files.walk(storageDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> {
                    try {
                        return mapper.readValue(p.toFile(), SubscriptionActive.class);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read subscription file: " + p, e);
                    }
                })
                .collect(Collectors.toList());
        }
        return subscriptions;
    }
    
    // === GET BY PARTY ID ===

    public List<SubscriptionActive> getByRelatedPartyId(String id) throws IOException {
        return loadAllFromStorage().stream()
            .filter(subscription -> subscription.getRelatedParties() != null)
            .filter(subscription -> subscription.getRelatedParties().stream()
                .anyMatch(party -> party != null 
                    && party.getId() != null
                    && party.getId().equals(id)))
            .collect(Collectors.toList());
    }
    
    // === CREATE ===

   /* public String saveToStorage(SubscriptionActive subscription, String filename) throws IOException {
        
        Files.createDirectories(storageDir);

        Path filePath = storageDir.resolve(filename.endsWith(".json") ? filename : filename + ".json");
        String json = mapper.writeValueAsString(subscription);
        Files.write(filePath, json.getBytes());

        return filePath.toString();
    }

    public String saveRawJsonToStorage(String rawJson, String filename) throws IOException {
        
        Files.createDirectories(storageDir);

        Path filePath = storageDir.resolve(filename);
        Files.write(filePath, rawJson.getBytes());

        return filePath.toString();
    }
    
    // === DELETE ===
    
    private Path findPlanFile(String subName) throws IOException {
        return Files.walk(storageDir)
            .filter(p -> p.getFileName().toString().equalsIgnoreCase(subName + ".json"))
            .findFirst()
            .orElseThrow(() -> new IOException("Plan not found: " + subName));
    }
    
    public void deleteSub(String subName) throws IOException {
        Path filePath = findPlanFile(subName);
        Files.delete(filePath);
    }

    // === UPDATE ===
    
    public String updateSubscription(String subName, SubscriptionActive updatedSubscription) throws IOException {
        Path filePath = findPlanFile(subName);
        String json = mapper.writeValueAsString(updatedSubscription);
        Files.write(filePath, json.getBytes());
        return filePath.toString();
    }*/
    
    
}