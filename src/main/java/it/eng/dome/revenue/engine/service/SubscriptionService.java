package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.CategoryApis;
import it.eng.dome.revenue.engine.mapper.RevenueProductMapper;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf620.v4.model.Category;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf637.v4.model.Product;

@Service
public class SubscriptionService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

	/** Dome Operator ID - now parametric via Spring property */
    @Value("${dome.operator.id}")
    private String DOME_OPERATOR_ID;

	@Autowired
	private TmfCachedDataRetriever tmfDataRetriever;
	
	@Autowired
	private TmfApiFactory tmfApiFactory;

	public SubscriptionService() {
	}

	@Override
	public void afterPropertiesSet() throws Exception {
	}
   

	/*
	 * Retrieves a subscription by its product ID.
	 */
    public Subscription getSubscriptionByProductId(String productId) {
    	
    	 if (productId == null || productId.isEmpty()) {
             throw new IllegalArgumentException("Product ID cannot be null or empty");
         }

         logger.info("Fetching subscription from product id: {}", productId);
         
         Product prod = this.tmfDataRetriever.getProductById(productId, null);

         return RevenueProductMapper.toSubscription(prod);
    }
    
    /**
	 * Retrieves all subscriptions associated with the DOME operator.
	 * 
	 * @return A list of Subscription objects.
	*/
    public List<Subscription> getAllSubscriptions() {
    	logger.info("Fetching subscriptions from tmf products");


        CategoryApis categoryApis = new CategoryApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient());
        List<Category> listCategory = categoryApis.getAllCategory(null, null);

        List<String> offeringIds = new ArrayList<>();
        for (Category c : listCategory) {
            if ("DOME OPERATOR Plan".equalsIgnoreCase(c.getName())) {
                List<ProductOffering> pos = tmfDataRetriever.getAllProductOfferings(null, Map.of("category.id", c.getId()));
                for (ProductOffering po : pos) {
                    offeringIds.add(po.getId());
                }
            }
        }

        logger.debug("Found product offerings: {}", offeringIds);

        Map<String, String> filter = new HashMap<>();
        filter.put("productOffering.id", String.join(",", offeringIds)); // OR

		List<Product> prods = this.tmfDataRetriever.getAllProducts(null, filter);

		List<Subscription> subs = new ArrayList<>();
        for (Product prod : prods) {
				Product fullProduct = this.tmfDataRetriever.getProductById(prod.getId(), null);
	            
				// FIXME: but be careful with last invoices... sub might not be active
				if (!"active".equalsIgnoreCase(fullProduct.getStatus().getValue())) {
	                continue;
	            }
				subs.add(RevenueProductMapper.toSubscription(fullProduct));				
        }	
				
        return subs;
    }

	/**
	 * Retrieves a subscription by its related party ID.
	 * 
	 * @param id The ID of the related party to search for.
	 * @return The Subscription object if found, null otherwise.
	*/
	public Subscription getSubscriptionByRelatedPartyId(String id) {
		// FIXME: this only returns the first subscription!!!!
		logger.debug("Retrieving subscription by related party id: {}", id);
	    return getAllSubscriptions().stream()
	            .filter(subscription -> subscription.getRelatedParties() != null)
	            .filter(subscription -> subscription.getRelatedParties().stream()
	                    .anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id)))
	            .findFirst()
	            .orElse(null);
	}
	
	/**
	 * Retrieves a list of subscriptions by related party ID and role.
	 * @param id related party id
	 * @param role role of related party
	 */
	public List<Subscription> getSubscriptionsByRelatedPartyId(String id, String role) {
		//logger.debug("Retrieving subscriptions by related party id: {} with role: {}", id, role);
	    return getAllSubscriptions().stream()
	            .filter(subscription -> subscription.getRelatedParties() != null)
	            .filter(subscription -> subscription.getRelatedParties().stream()
					.anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id) && party.getRole()!=null && party.getRole().equalsIgnoreCase(role)) 
				)
				.toList();
	}

	/**
	 * Retrieves all subscriptions associated with a specific plan ID.
	 * 
	 * @param id The ID of the plan to filter subscriptions by.
	 * @return A list of Subscription objects that match the given plan ID.
	*/
	public List<Subscription> getSubscriptionsByPlanId(String id) {
	    logger.debug("Retrieving subscriptions by plan ID: {}", id);
        return this.getAllSubscriptions().stream()
                .filter(sub -> sub.getPlan() != null && id.equals(sub.getPlan().getId()))
                .collect(Collectors.toList());
	}	
}