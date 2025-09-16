package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.ProductApis;
import it.eng.dome.revenue.engine.mapper.RevenueProductMapper;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.RelatedParty;

@Service
public class SubscriptionService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

	// FIXME: make this parametric
	private static String DOME_OPERATOR_ID = "urn:ngsi-ld:organization:a195013a-a0e4-493a-810a-b040e10da58f"; //GOLEM-DOME DEV
	//private static String DOME_OPERATOR_ID = "urn:ngsi-ld:organization:95fdc12e-6889-4f08-8ff8-296b10e8e781"; // DOME OPERATOR MARKT SBX


    @Autowired
    // Factory for TMF APIss
    private TmfApiFactory tmfApiFactory;

    // API to retrieve product
    private ProductApis productApis;
   
	public SubscriptionService() {
	}
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.productApis = new ProductApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
        logger.info("SubscriptionService initialized with orgApi {}", this.productApis);
    }
	
    public Subscription getSubscriptionByProductId(String productId) {
    	
    	 if (productId == null || productId.isEmpty()) {
             throw new IllegalArgumentException("Product ID cannot be null or empty");
         }

         logger.info("Fetching subscription from product id: {}", productId);
         
         Product prod = this.productApis.getProduct(productId, null);

         return RevenueProductMapper.toSubscription(prod);
    }
    
    public List<Subscription> getAllSubscriptions() {
    	logger.info("Fetching subscriptions from products");
    	        
        logger.info("Using the productAPIs {}", this.productApis);
		Map<String, String> filter = new HashMap<>();
		filter.put("relatedParty.id", DOME_OPERATOR_ID);
        List<Product> prods = productApis.getAllProducts(null, filter);
        
        List<Subscription> subs = new ArrayList<>();
        
        for (Product prod : prods) {
			// FIXME: weak control con products to guess it's a subscription
			if(prod.getName()!=null && prod.getName().toLowerCase().indexOf("subscription")!=-1) {
				// FIXME: workaround for bug in tmf. Need to retrieve the product individually.
				Product fullProduct = productApis.getProduct(prod.getId(), null);
				if(fullProduct.getRelatedParty()!=null) {
					for(RelatedParty rp: fullProduct.getRelatedParty()) {
						if(rp!=null && DOME_OPERATOR_ID.equals(rp.getId()) && "seller".equalsIgnoreCase(rp.getRole())) {
							subs.add(RevenueProductMapper.toSubscription(fullProduct));
						}
					}
				}
			}
		}
        
        return subs;
    }

	/**
	 * Retrieves a subscription by its related party ID.
	 * 
	 * @param id The ID of the related party to search for.
	 * @return The Subscription object if found, null otherwise.
	 * @throws IOException If there is an error reading the subscription data.
	 * @throws ApiException If there is an error retrieving the organization.
	*/
	public Subscription getSubscriptionByRelatedPartyId(String id) throws IOException, ApiException {
		// FIXME: this only returns the first subscription!!!!
		logger.debug("Retrieving subscription by related party id: {}", id);
	    return getAllSubscriptions().stream()
	            .filter(subscription -> subscription.getRelatedParties() != null)
	            .filter(subscription -> subscription.getRelatedParties().stream()
	                    .anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id)))
	            .findFirst()
	            .orElse(null);
	}
	
	public List<Subscription> getSubscriptionsByRelatedPartyId(String id, String role) throws IOException, ApiException {
		logger.debug("Retrieving subscription by related party id: {}", id);
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
	 * @throws IOException If there is an error reading the subscription data.
	 * @throws ApiException If there is an error retrieving the organization.
	*/
	public List<Subscription> getSubscriptionsByPlanId(String id) {
	    logger.debug("Retrieving subscriptions by plan ID: {}", id);
        return this.getAllSubscriptions().stream()
                .filter(sub -> sub.getPlan() != null && id.equals(sub.getPlan().getId()))
                .collect(Collectors.toList());
	}	
}