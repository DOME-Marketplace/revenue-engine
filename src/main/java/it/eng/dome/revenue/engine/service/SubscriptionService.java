package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.ehcache.Cache;
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

//    // TMForum API to retrieve bills
//    private OrganizationApi orgApi;
    
    // API to retrieve product
    private ProductApis productApis;
//
//    @Autowired
//    private TmfDataRetriever tmfDataRetriever;

//    @Autowired
//    private PlanService planService;    
	
    private Cache<String, List<Subscription>> subscriptionCache;
   
	public SubscriptionService(CacheService cacheService) {
        subscriptionCache = cacheService.getOrCreateCache(
                "subscriptionCache",
                String.class,
                (Class<List<Subscription>>)(Class<?>)List.class,
                Duration.ofMinutes(30)
            );
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
//        this.orgApi = new OrganizationApi(tmfApiFactory.getTMF632PartyManagementApiClient());
        productApis = new ProductApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
        logger.info("SubscriptionService initialized with orgApi");
    }
	
    public Subscription getSubscriptionByProductId(String productId) {
    	
    	 if (productId == null || productId.isEmpty()) {
             throw new IllegalArgumentException("Product ID cannot be null or empty");
         }

         logger.info("Fetching subscription from product id: {}", productId);
         
         Product prod = productApis.getProduct(productId, null);

         return RevenueProductMapper.toSubscription(prod);
    }
    
    public List<Subscription> getAllSubscriptionsByProducts() {
    	logger.info("Fetching subscriptions from products");
    	
		if (subscriptionCache != null) {
			List<Subscription> cachedSubscriptions = subscriptionCache.get("all_subscriptions");
			if (cachedSubscriptions != null && !cachedSubscriptions.isEmpty()) {
				logger.info("Found {} subscriptions in cache", cachedSubscriptions.size());
				return cachedSubscriptions;
			}
		}
        
    	//TODO: check how to filter product of a sub
        List<Product> prods = productApis.getAllProducts(null, null);
        
        List<Subscription> subs = new ArrayList<>();
        
        for (Product prod : prods) {
			subs.add(RevenueProductMapper.toSubscription(prod));
		}
        
		// cache the subscriptions
		subscriptionCache.put("all_subscriptions", subs);
		logger.info("Cached {} subscriptions", subs.size());

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
		logger.debug("Retrieving subscription by related party id: {}", id);
	    return getAllSubscriptionsByProducts().stream()
	            .filter(subscription -> subscription.getRelatedParties() != null)
	            .filter(subscription -> subscription.getRelatedParties().stream()
	                    .anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id)))
	            .findFirst()
	            .orElse(null);
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
    
    /**
	 * Retrieves a subscription by its ID.
	 * 
	 * @param id The ID of the subscription to retrieve.
	 * @return The Subscription object if found, null otherwise.
	 * @throws ApiException If there is an error retrieving the organization or plan.
	 * @throws IOException If there is an error reading the plan data.
	 */
//	public Subscription getSubscriptionById(String id) throws ApiException, IOException {
//		logger.info("Fetch subscription with ID {}", id);
//
//		// check the id is in the right format
//		if (id == null || id.isEmpty() || id.length()!=98) {
//			logger.error("malformed subscription id: " + id);
//			return null;
//		}
//		
//		String organisationId = "urn:ngsi-ld:organization:"+id.substring(25, 61);
//		logger.debug("Retrieved organisationId: " + organisationId);
//		String planId = "urn:ngsi-ld:plan:"+id.substring(62, 98);
//		logger.debug("Retrieved planId: " + planId);
//
//		// retrieve the organization
//		Organization organization = this.orgApi.retrieveOrganization(organisationId, null);
//		logger.debug("TradingName organization: {}", organization.getTradingName());
//
//		// retrieve the plan
//		Plan plan = planService.findPlanById(planId);
//		logger.debug("Retrieved plan: " + plan);
//
//		// create the subscription
//		Subscription subscription = this.createSubscription(id, organization, plan);
//			
//		return subscription;
//	}

	/**
	 * Creates a new subscription with the given ID, organization, and plan.
	 * 
	 * @param id The ID of the subscription to create.
	 * @param organization The organization associated with the subscription.
	 * @param plan The plan associated with the subscription.
	 * @return The created Subscription object.
	 * @throws ApiException If there is an error during API operations.
	 * @throws IOException If there is an error reading the plan data.
	*/
//	private Subscription createSubscription(String id, Organization organization, Plan plan) throws ApiException, IOException {
//		
//		logger.debug("Creating subscription with id: {}", id);
//
//		// create the subscription
//		Subscription subscription = new Subscription();
//		subscription.setId(id);
//		subscription.setName("Subscription for " + organization.getTradingName() + " on plan " + plan.getName());
//		// embed a plan reference
//		subscription.setPlan(plan.buildRef());
//		// embed an organisation ref
//		RelatedParty party = new RelatedParty();
//		party.setId(organization.getId());
//		party.setName(organization.getTradingName());
//		party.setRole("Buyer");
//		party.setAtReferredType("Organization");
//		List<RelatedParty> relatedParties = new ArrayList<>();
//		relatedParties.add(party);
//		// also embed the DOME operator
//		relatedParties.add(this.getFakeDomeOperatorParty());
//
//		subscription.setRelatedParties(relatedParties);
//
//		// status and start date
//		subscription.setStatus("active");
//		// default start time for everybody is 5 July 2025
//		OffsetDateTime startTime = OffsetDateTime.parse("2025-05-05T00:00:00+00:00");
//		subscription.setStartDate(startTime);	
//
//		return subscription;
//	}

//	private RelatedParty getFakeDomeOperatorParty() {
//		RelatedParty dome = new RelatedParty();
//		dome.setId("urn:ngsi-ld:organization:24d2ea66-0cd4-4396-a8b5-ea5fd8bf2bdd");
//		dome.setName("The DOME Operator");
//		dome.setRole("Seller");
//		dome.setAtReferredType("Organization");
//		return dome;
//	}

	/**
	 * Retrieves all subscriptions for all organizations.
	 * 
	 * @return A list of Subscription objects representing all subscriptions.
	 * @throws ApiException If there is an error retrieving the organizations.
	 * @throws IOException If there is an error reading the plan data.
	 */
//	public List<Subscription> getAllSubscriptions() throws ApiException, IOException {
//		logger.info("Get all subscriptions");
//		// check the cache first
//		if (subscriptionCache != null) {
//			List<Subscription> cachedSubscriptions = subscriptionCache.get("all_subscriptions");
//			if (cachedSubscriptions != null && !cachedSubscriptions.isEmpty()) {
//				logger.info("Found {} subscriptions in cache", cachedSubscriptions.size());
//				return cachedSubscriptions;
//			}
//		}
//		
//		// retrieve all organizations
//		List<Organization> organizations = this.orgApi.listOrganization(null, null, null, null);
//		
//		logger.info("Number of organization found: {}", organizations.size());
//
//		// build the subscription and add it to the output
//		List<Subscription> subscriptions = new ArrayList<>();
//		for(Organization o:organizations) {
//			
//			logger.debug("Analysing organizationId: {}", o.getId());
//			
//			// identify a default plan for each organization.
//			Plan plan = this.getPlanForOrganization(o);
//			
//			// create the subscription
//			String subscriptionId = "urn:ngsi-ld:subscription:" + o.getId().substring("urn:ngsi-ld:organization:".length()) + "-" + plan.getId().substring("urn:ngsi-ld:plan:".length());
//			logger.debug("Subscription id: {}", subscriptionId);
//			
//			Subscription subscription = this.createSubscription(subscriptionId, o, plan);
//			
//			subscriptions.add(subscription);
//		}
//		
//		logger.info("Subscriptions size: {}", subscriptions.size());
//		// cache the subscriptions
//		if (subscriptionCache != null) {
//			subscriptionCache.put("all_subscriptions", subscriptions);
//			logger.info("Cached {} subscriptions", subscriptions.size());
//		} else {
//			logger.warn("No cache available, subscriptions will not be cached");
//		}
//		
//		return subscriptions;
//	}
//
//	private Plan getPlanForOrganization(Organization organization) throws IOException {
//		Character c = organization.getId().charAt(25);
//		if(Character.isDigit(c)) {
//			// basic
//			return planService.findPlanById("urn:ngsi-ld:plan:02645de3-8c7y-1276-a344-00rfl123aq1n");
//		} else {
//			// pro
//			return planService.findPlanById("urn:ngsi-ld:plan:52761it5-3d4q-2321-p009-44bro198re0p");
//		}
//	}

//    public Product buildProduct(Subscription subscription) {
//        if (subscription == null || subscription.getRelatedParties() == null || subscription.getRelatedParties().isEmpty()) {
//            throw new IllegalArgumentException("Missing related party information in Subscription");
//        }
//        
//        // Retrieve the related party with role = "Buyer"
//        RelatedParty buyerParty = subscription.getRelatedParties().stream()
//            .filter(rp -> "Buyer".equalsIgnoreCase(rp.getRole()))
//            .findFirst()
//            .orElseThrow(() -> new IllegalArgumentException("No related party with role 'Buyer' found"));
//        
//        BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(buyerParty.getId());
//        return RevenueProductMapper.toProduct(subscription, billingAccountRef);
//    }
	
}