package it.eng.dome.revenue.engine.service;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.model.SubscriptionPlan;
 
@Service
public class SubscriptionPlanLoader {
 
    private final ObjectMapper mapper;
 
    public SubscriptionPlanLoader() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())        // LocalDate
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
 
    public SubscriptionPlan loadFromClasspath(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(in, SubscriptionPlan.class);
        }
    }
    
    

}