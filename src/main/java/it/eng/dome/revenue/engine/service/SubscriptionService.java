package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.Range;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionPlan;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;

@Service
public class SubscriptionService {
	
	private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

	private final Path storageDir = Paths.get("src/main/resources/data/subscriptions/");

	private final ObjectMapper mapper;

	private final SubscriptionPlanService subscriptionPlanService;

	@Autowired
	private TmfDataRetriever tmfDataRetriever;

	public SubscriptionService() {
		this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		this.subscriptionPlanService = new SubscriptionPlanService();
	}


    // === GET BY Id===
    public Subscription getBySubscriptionId(String id) throws IOException {
        return loadAllFromStorage().stream()
                .filter(sub -> sub.getId() != null && sub.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
	// === GET ALL ===

	public List<Subscription> loadAllFromStorage() throws IOException {

		List<Subscription> subscriptions = new ArrayList<>();

		if (Files.exists(storageDir)) {
			return Files.walk(storageDir).filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json"))
					.map(p -> {
						try {
							return mapper.readValue(p.toFile(), Subscription.class);
						} catch (IOException e) {
							throw new RuntimeException("Failed to read subscription file: " + p, e);
						}
					}).collect(Collectors.toList());
		}
		return subscriptions;
	}

	// === GET BY PARTY ID ===

	public List<Subscription> getByRelatedPartyId(String id) throws IOException {
		return loadAllFromStorage().stream().filter(subscription -> subscription.getRelatedParties() != null)
				.filter(subscription -> subscription.getRelatedParties().stream()
						.anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id)))
				.collect(Collectors.toList());
	}




}