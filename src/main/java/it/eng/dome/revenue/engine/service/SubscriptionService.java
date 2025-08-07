package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.mapper.RevenueProductMapper;
import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;

@Service
public class SubscriptionService implements InitializingBean {
	

	private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Autowired
    // Factory for TMF APIss
    private TmfApiFactory tmfApiFactory;

    // TMForum API to retrieve bills
    private OrganizationApi orgApi;

    @Autowired
    private TmfDataRetriever tmfDataRetriever;
//	private final Path storageDir = Paths.get("src/main/resources/data/subscriptions/");
//
//	private final ObjectMapper mapper;
//
//	public SubscriptionService() {
//		this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
//				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//		new PlanService();
//	}
    @Autowired
    private PlanService planService;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.orgApi = new OrganizationApi(tmfApiFactory.getTMF632PartyManagementApiClient());
        logger.info("SubscriptionService initialized with orgApi");
    }
	
	/*
	 * Temporary solution.
	 * For each organisation, assume the subscription of a Basic 2025 plan.
	 * The id of the subscription is the concatenation of the organisation and the plan.
	 */


    // === GET BY Id===
	/*
	@Deprecated
    public Subscription getBySubscriptionId(String id) throws IOException {
        return loadAllFromStorage().stream()
                .filter(sub -> sub.getId() != null && sub.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
	*/

    /**
	 * Retrieves a subscription by its ID.
	 * 
	 * @param id The ID of the subscription to retrieve.
	 * @return The Subscription object if found, null otherwise.
	 * @throws ApiException If there is an error retrieving the organization or plan.
	 * @throws IOException If there is an error reading the plan data.
	 */
    
	public Subscription getSubscriptionById(String id) throws ApiException, IOException {
		logger.info("Fetch subscription with ID {}", id);
		
		// check the id is in the right format
		if (id == null || id.isEmpty() || id.length()!=98) {
			logger.error("malformed subscription id: " + id);
			return null;
		}
		
		String organisationId = "urn:ngsi-ld:organization:"+id.substring(25, 61);
		logger.debug("Retrieved organisationId: " + organisationId);
		String planId = "urn:ngsi-ld:plan:"+id.substring(62, 98);
		logger.debug("Retrieved planId: " + planId);

		// retrieve the organization
		Organization organization = this.orgApi.retrieveOrganization(organisationId, null);
		logger.debug("TradingName organization: {}", organization.getTradingName());

		// retrieve the plan
		Plan plan = planService.findPlanById(planId);
		logger.debug("Retrieved plan: " + plan);

		// create the subscription
		Subscription subscription = this.createSubscription(id, organization, plan);
		return subscription;
	}

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
	private Subscription createSubscription(String id, Organization organization, Plan plan) throws ApiException, IOException {
		
		logger.debug("Creating subscription with id: {}", id);

		// create the subscription
		Subscription subscription = new Subscription();
		subscription.setId(id);
		subscription.setName("Subscription for " + organization.getTradingName() + " on plan " + plan.getName());
		// embed a plan reference
		subscription.setPlan(plan.buildRef());
		// embed an organisation ref
		RelatedParty party = new RelatedParty();
		party.setId(organization.getId());
		party.setName(organization.getTradingName());
		party.setRole("Buyer");
		party.setAtReferredType("Organization");
		List<RelatedParty> relatedParties = new ArrayList<>();
		relatedParties.add(party);
		// also embed the DOME operator
		relatedParties.add(this.getFakeDomeOperatorParty());

		subscription.setRelatedParties(relatedParties);

		// status and start date
		subscription.setStatus("active");
		// default start time for everybody is 5 July 2025
		OffsetDateTime startTime = OffsetDateTime.parse("2025-05-05T00:00:00+00:00");
		subscription.setStartDate(startTime);	

		return subscription;
	}

	private RelatedParty getFakeDomeOperatorParty() {
		RelatedParty dome = new RelatedParty();
		dome.setId("urn:ngsi-ld:organization:24d2ea66-0cd4-4396-a8b5-ea5fd8bf2bdd");
		dome.setName("The DOME Operator");
		dome.setRole("Seller");
		dome.setAtReferredType("Organization");
		return dome;
	}

	/**
	 * Retrieves all subscriptions for all organizations.
	 * 
	 * @return A list of Subscription objects representing all subscriptions.
	 * @throws ApiException If there is an error retrieving the organizations.
	 * @throws IOException If there is an error reading the plan data.
	 */
	public List<Subscription> getAllSubscriptions() throws ApiException, IOException {
		logger.info("Get all subscriptions");
		
		// retrieve all organizations
		List<Organization> organizations = this.orgApi.listOrganization(null, null, null, null);
		
		logger.info("Number of organization found: {}", organizations.size());

		// build the subscription and add it to the output
		List<Subscription> subscriptions = new ArrayList<>();
		for(Organization o:organizations) {
			
			logger.debug("Analysing organizationId: {}", o.getId());
			
			// identify a default plan for each organization.
			Plan plan = this.getPlanForOrganization(o);
			
			// create the subscription
			String subscriptionId = "urn:ngsi-ld:subscription:" + o.getId().substring("urn:ngsi-ld:organization:".length()) + "-" + plan.getId().substring("urn:ngsi-ld:plan:".length());
			logger.debug("Subscription id: {}", subscriptionId);
			
			Subscription subscription = this.createSubscription(subscriptionId, o, plan);
			subscriptions.add(subscription);
		}
		
		logger.info("Subscriptions size: {}", subscriptions.size());
		
		return subscriptions;
	}

	private Plan getPlanForOrganization(Organization organization) throws IOException {
		Character c = organization.getId().charAt(25);
		if(Character.isDigit(c)) {
			// basic
			return planService.findPlanById("urn:ngsi-ld:plan:02645de3-8c7y-1276-a344-00rfl123aq1n");
		} else {
			// pro
			return planService.findPlanById("urn:ngsi-ld:plan:52761it5-3d4q-2321-p009-44bro198re0p");
		}
	}


	// === GET ALL ===
	/*
	@Deprecated
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
	*/

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
	    return getAllSubscriptions().stream()
	            .filter(subscription -> subscription.getRelatedParties() != null)
	            .filter(subscription -> subscription.getRelatedParties().stream()
	                    .anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id)))
	            .findFirst()
	            .orElse(null);
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
	    try {
	        return this.getAllSubscriptions().stream()
	                .filter(sub -> sub.getPlan() != null && id.equals(sub.getPlan().getId()))
	                .collect(Collectors.toList());
	    } catch (IOException | ApiException e) {
	        throw new RuntimeException("Unable to load subscriptions for planId: " + id, e);
	    }
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

    public Product buildProduct(Subscription subscription) {
        if (subscription == null || subscription.getRelatedParties() == null || subscription.getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in Subscription");
        }
        
        // Retrieve the related party with role = "Buyer"
        RelatedParty buyerParty = subscription.getRelatedParties().stream()
            .filter(rp -> "Buyer".equalsIgnoreCase(rp.getRole()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No related party with role 'Buyer' found"));
        
        BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(buyerParty.getId());
        return RevenueProductMapper.toProduct(subscription, billingAccountRef);
    }
	
}