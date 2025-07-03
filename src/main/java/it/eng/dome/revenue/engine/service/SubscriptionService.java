package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.Range;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionPlan;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;

@Service
public class SubscriptionService {
	
	private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

	private final Path storageDir = Paths.get("src/main/resources/sample_data/sub/");

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

	// === GET BY FILENAME===

//    public SubscriptionActive loadFromClasspath(String path) throws IOException {
//        try (var in = new ClassPathResource(path).getInputStream()) {
//            return mapper.readValue(in, SubscriptionActive.class);
//        }
//    }

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

	// === GET FEE PARTY ID ===

	public Double getFeeByRelatedPartyId(String partyId) throws IOException {

		// retrieve Subscription of RP by ID
		List<Subscription> subscription = getByRelatedPartyId(partyId);
		logger.debug("Size list subOfRLId: {} ", subscription.size());

		//TODO check which status insert in filter
		// Filter only sub with status "active"
		List<Subscription> activeSubs = subscription.stream()
				.filter(sub -> "active".equalsIgnoreCase(sub.getStatus())).collect(Collectors.toList());
		if (activeSubs.size() == 0) {
			logger.warn("No active subscription found for Related Party with ID {}", partyId);
			return null;
		}
		if (activeSubs.size() > 1) {
			logger.warn("Multiple active subscriptions found for Related Party ID {}", partyId);
			return null;
		}

		// Get only 1 sub (active)
		SubscriptionPlan plan = activeSubs.get(0).getPlan();

		// retrieve all info of plan
		plan = subscriptionPlanService.findPlanById(plan.getId());

		List<Price> prices = plan.getPrice();

		//TODO check if needs activation fee
		// get fixed fee
		Double fixedFee = findFixedFee(prices);
		
		// retrieve sales amount
		// get bills for seller RP in last month
		List<AppliedCustomerBillingRate> bills = new ArrayList<>();
		try {
			//TODO create a new function for bills retrieve in a range of time period
			bills = tmfDataRetriever.retrieveBillsForSellerInLastMonth(partyId);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Errore nel recupero dei bills per sellerId " + partyId, e);
		}

		// Sum of taxIncludedAmount.value only if bills non empty
		Double sales = bills.stream()
				.filter(b -> b.getTaxIncludedAmount() != null && b.getTaxIncludedAmount().getValue() != null)
				.mapToDouble(b -> b.getTaxIncludedAmount().getValue()).sum();

		// get commissions of sales volume
		Double percentCommission = findTransactionFeePercent(prices, sales);

		// TODO calculate discount
		//Double percentDiscount = findDiscountPercent(prices, sales);

		// if percent is positive than calculate commissions of sales volume
		if (percentCommission != null && percentCommission > 0) {
			Double salesCommissions = percentCommission * sales / 100;
			logger.info("Fixed fee: {}, Percent Commission: {}%, Sales: {}", fixedFee, percentCommission, sales);
			return fixedFee + salesCommissions; // calculate sum of all fee and commissions
		}

		logger.info("Fixed fee: {}, Sales: {}", fixedFee, sales);
		return fixedFee;
	}
	
	private Double findFixedFee(List<Price> prices) {
	    if (prices == null)
	        return null;
	    for (Price price : prices) {
	        // Se è bundle, scendi ricorsivamente
	        if (Boolean.TRUE.equals(price.getIsBundle()) && price.getPrices() != null) {
	            Double result = findFixedFee(price.getPrices());
	            if (result != null)
	                return result;
	        }
	        // Se è "Recurring Yearly Fee", restituisci l'importo
	        if ("recurring yearly fee".equalsIgnoreCase(price.getName())) {
	            return price.getAmount();
	        }
	    }
	    return null;
	}

	private Double findTransactionFeePercent(List<Price> prices, Double sales) {
		if (prices == null)
			return null;

		for (Price price : prices) {
			// if is bundle, scendi ricorsivamente
			if (Boolean.TRUE.equals(price.getIsBundle()) && price.getPrices() != null) {
				Double result = findTransactionFeePercent(price.getPrices(), sales);
				if (result != null)
					return result;
			}
			// Altrimenti, check if is Transaction-based Fee
			if ("Transaction-based Fee".equalsIgnoreCase(price.getName()) && price.getPrices() != null) {
				for (Price innerPrice : price.getPrices()) {
					Range range = innerPrice.getApplicableBaseRange();
					double min = (range != null && range.getMin() != null) ? range.getMin() : Double.NEGATIVE_INFINITY;
					double max = (range != null && range.getMax() != null) ? range.getMax() : Double.POSITIVE_INFINITY;
					if (sales >= min && sales <= max) {
						return innerPrice.getPercent();
					}
				}
			}
		}

		return null;
	}
	
	private Double findDiscountPercent(List<Price> prices, Double salesThresholds) {
	    if (prices == null) return null;

	    for (Price price : prices) {
	        // Se il price è un bundle, ricorsivamente cerca negli elementi interni
	        if (Boolean.TRUE.equals(price.getIsBundle()) && price.getPrices() != null) {
	            Double result = findDiscountPercent(price.getPrices(), salesThresholds);
	            if (result != null)
	                return result;
	        }

	        // Scorri i discount di questo price
	        List<Discount> discounts = price.getDiscounts();
	        if (discounts != null) {
	            for (Discount discount : discounts) {
	                Double result = findDiscountPercentFromDiscount(discount, salesThresholds);
	                if (result != null)
	                    return result;
	            }
	        }
	    }
	    return null;
	}

	private Double findDiscountPercentFromDiscount(Discount discount, Double salesThresholds) {
	    if (Boolean.TRUE.equals(discount.getIsBundle()) && discount.getDiscounts() != null) {
	        // Se il discount è un bundle, ricorsivamente cerca negli sconti interni
	        for (Discount nested : discount.getDiscounts()) {
	            Double result = findDiscountPercentFromDiscount(nested, salesThresholds);
	            if (result != null)
	                return result;
	        }
	    } else {
	        Range range = discount.getApplicableBaseRange();
	        if (range != null) {
	            Double min = range.getMin() != null ? range.getMin() : Double.NEGATIVE_INFINITY;
	            Double max = range.getMax() != null ? range.getMax() : Double.POSITIVE_INFINITY;

	            // Verifica se salesThresholds è nel range applicabile
	            if (salesThresholds >= min && salesThresholds <= max) {
	                return discount.getPercent();
	            }
	        }
	    }
	    return null;
	}  
	
	// === CREATE ===

	/*
	 * public String saveToStorage(SubscriptionActive subscription, String filename)
	 * throws IOException {
	 * 
	 * Files.createDirectories(storageDir);
	 * 
	 * Path filePath = storageDir.resolve(filename.endsWith(".json") ? filename :
	 * filename + ".json"); String json = mapper.writeValueAsString(subscription);
	 * Files.write(filePath, json.getBytes());
	 * 
	 * return filePath.toString(); }
	 * 
	 * public String saveRawJsonToStorage(String rawJson, String filename) throws
	 * IOException {
	 * 
	 * Files.createDirectories(storageDir);
	 * 
	 * Path filePath = storageDir.resolve(filename); Files.write(filePath,
	 * rawJson.getBytes());
	 * 
	 * return filePath.toString(); }
	 * 
	 * // === DELETE ===
	 * 
	 * private Path findPlanFile(String subName) throws IOException { return
	 * Files.walk(storageDir) .filter(p ->
	 * p.getFileName().toString().equalsIgnoreCase(subName + ".json")) .findFirst()
	 * .orElseThrow(() -> new IOException("Plan not found: " + subName)); }
	 * 
	 * public void deleteSub(String subName) throws IOException { Path filePath =
	 * findPlanFile(subName); Files.delete(filePath); }
	 * 
	 * // === UPDATE ===
	 * 
	 * public String updateSubscription(String subName, SubscriptionActive
	 * updatedSubscription) throws IOException { Path filePath =
	 * findPlanFile(subName); String json =
	 * mapper.writeValueAsString(updatedSubscription); Files.write(filePath,
	 * json.getBytes()); return filePath.toString(); }
	 */

}