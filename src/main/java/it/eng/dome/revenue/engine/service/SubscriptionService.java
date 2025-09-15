package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

@Service
public class SubscriptionService implements InitializingBean {
	
	private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

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
    
    public List<Subscription> getAllSubscriptionsByProducts() {
    	logger.info("Fetching subscriptions from products");
    	        
    	// TODO: check how to filter product of a sub
		// TODO: at least filter by seller (DOME operator)
        logger.info("Using the productAPIs {}", this.productApis);
        List<Product> prods = productApis.getAllProducts(null, null);
        
        List<Subscription> subs = new ArrayList<>();
        
        for (Product prod : prods) {
			// FIXME: weak control con products to guess it's a subscription
			if(prod.getName()!=null && prod.getName().toLowerCase().indexOf("subscription")!=-1) {
				// FIXME: workaround for bug in tmf. Need to retrieve the product individually.
				Product fullProduct = productApis.getProduct(prod.getId(), null);
				subs.add(RevenueProductMapper.toSubscription(fullProduct));
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
	    return getAllSubscriptionsByProducts().stream()
	            .filter(subscription -> subscription.getRelatedParties() != null)
	            .filter(subscription -> subscription.getRelatedParties().stream()
	                    .anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id)))
	            .findFirst()
	            .orElse(null);
	}
	
	public List<Subscription> getSubscriptionsByPartyId(String id) throws IOException, ApiException {
		logger.debug("Retrieving subscription by related party id: {}", id);
	    return getAllSubscriptionsByProducts().stream()
	            .filter(subscription -> subscription.getRelatedParties() != null)
	            .filter(subscription -> subscription.getRelatedParties().stream()
				.anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id)))
				.toList();
	}


	/**
	 *  Retrieves the subscription ID associated with a specific related party ID.
	 * 
	 * @param relatedPartyId The ID of the related party to search for.
	 * @return The ID of the subscription if found, null otherwise.
	 * @throws IOException If there is an error reading the subscription data.
	 * @throws ApiException If there is an error retrieving the organization.
	*/
	public String getSubscriptionIdByRelatedPartyId(String relatedPartyId) throws IOException, ApiException {
		logger.debug("Retrieving subscription id by related party id: {}", relatedPartyId);
	    Subscription subscription = getSubscriptionByRelatedPartyId(relatedPartyId);
	    return subscription != null ? subscription.getId() : null;
	}
	
	/**
	 * Retrieves all subscriptions associated with a specific plan ID.
	 * 
	 * @param id The ID of the plan to filter subscriptions by.
	 * @return A list of Subscription objects that match the given plan ID.
	 * @throws IOException If there is an error reading the subscription data.
	 * @throws ApiException If there is an error retrieving the organization.
	*/
	public List<Subscription> getByPlanId(String id) {
	    logger.debug("Retrieving subscriptions by plan ID: {}", id);
        return this.getAllSubscriptionsByProducts().stream()
                .filter(sub -> sub.getPlan() != null && id.equals(sub.getPlan().getId()))
                .collect(Collectors.toList());
	}
    	
}