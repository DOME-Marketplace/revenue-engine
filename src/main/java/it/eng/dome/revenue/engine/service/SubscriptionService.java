package it.eng.dome.revenue.engine.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.eng.dome.revenue.engine.model.Plan;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf632.v4.ApiException;
import it.eng.dome.tmforum.tmf632.v4.api.OrganizationApi;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;

import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;

@Service
public class SubscriptionService implements InitializingBean {
	

	private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Autowired
    // Factory for TMF APIss
    private TmfApiFactory tmfApiFactory;

    // TMForum API to retrieve bills
    private OrganizationApi orgApi;

	private final Path storageDir = Paths.get("src/main/resources/data/subscriptions/");

	private final ObjectMapper mapper;

	public SubscriptionService() {
		this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		new PlanService();
	}

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

	public Subscription getSubscriptionById(String id) throws ApiException, IOException {
		// 1. check the id is in the right format
		if (id == null || id.isEmpty() || id.length()!=98) {
			logger.info("malformed subscription id: " + id);
			return null;
		}
		String organisationId = "urn:ngsi-ld:organization:"+id.substring(25, 61);
		logger.debug("Retrieved organisationId: " + organisationId);
		String planId = "urn:ngsi-ld:plan:"+id.substring(62, 98);
		logger.debug("Retrieved planId: " + planId);

		// 2. retrieve the organization
		Organization organization = this.orgApi.retrieveOrganization(organisationId, null);
		logger.debug("Retrieved organization: " + organization);

		// 3. retrieve the plan
		Plan plan = new PlanService().findPlanById(planId);
		logger.debug("Retrieved plan: " + plan);

		// 4. create the subscription
		Subscription subscription = this.createSubscription(planId, organization, plan);
		return subscription;
	}

	private Subscription createSubscription(String id, Organization organization, Plan plan) throws ApiException, IOException {

		// 1. create the subscription
		Subscription subscription = new Subscription();
		subscription.setId(id);
		subscription.setName("Subscription for " + organization.getName() + " on plan " + plan.getName());
		// 1.1 embed a plan
		Plan planRef = new Plan();
		planRef.setId(plan.getId());
		planRef.setName(plan.getName());
		subscription.setPlan(planRef);
		// 1.2 embed an organisation ref
		RelatedParty party = new RelatedParty();
		party.setId(organization.getId());
		party.setName(organization.getTradingName());
		party.setRole("Buyer");
		List<RelatedParty> relatedParties = new ArrayList<>();
		relatedParties.add(party);
		subscription.setRelatedParties(relatedParties);

		// 2 status and start date
		subscription.setStatus("sctive");
		subscription.setStartDate(OffsetDateTime.now());

		return subscription;
	}


	public List<Subscription> getAllSubscriptions() throws ApiException, IOException {
		// 1. retrieve all organizations
		List<Organization> organizations = this.orgApi.listOrganization(null, null, 5, null);

		// 2. build the subscription and add it to the output
		List<Subscription> subscriptions = new ArrayList<>();
		for(Organization o:organizations) {
			// 2.1. identify a default plan for each organization.
			Plan plan = this.getPlanForOrganization(o);
			// 2.2 create the subscription
			String subscriptionId = "urn:ngsi-ld:subscription:" + o.getId().substring("urn:ngsi-ld:organization:".length()) + "-" + plan.getId().substring("urn:ngsi-ld:plan:".length());
			Subscription subscription = this.createSubscription(subscriptionId, o, plan);
			subscriptions.add(subscription);
		}
		return subscriptions;
	}

	private Plan getPlanForOrganization(Organization organization) throws IOException {
		Character c = organization.getId().charAt(25);
		if(Character.isDigit(c)) {
			// basic
			return new PlanService().findPlanById("urn:ngsi-ld:plan:02645de3-8c7y-1276-a344-00rfl123aq1n");
		} else {
			// pro
			return new PlanService().findPlanById("urn:ngsi-ld:plan:52761it5-3d4q-2321-p009-44bro198re0p");
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

	// === GET BY PARTY ID ===

	/*
	public List<Subscription> getByRelatedPartyId(String id) throws IOException {
		return loadAllFromStorage().stream().filter(subscription -> subscription.getRelatedParties() != null)
				.filter(subscription -> subscription.getRelatedParties().stream()
						.anyMatch(party -> party != null && party.getId() != null && party.getId().equals(id)))
				.collect(Collectors.toList());
	}
	*/

	public List<Subscription> getByPlanId(String id) {
	    try {
	        return this.getAllSubscriptions().stream()
	                .filter(sub -> sub.getPlan() != null && id.equals(sub.getPlan().getId()))
	                .collect(Collectors.toList());
	    } catch (IOException e) {
	        throw new RuntimeException("Unable to load subscriptions for planId: " + id, e);
	    } catch (ApiException e) {
	        throw new RuntimeException("Unable to load subscriptions for planId: " + id, e);
	    }
	}
	
}