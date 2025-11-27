package it.eng.dome.revenue.engine.service;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.mapper.RevenueProductMapper;
import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.utils.RelatedPartyUtils;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubscriptionService implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    /** Dome Operator ID - now parametric via Spring property */
    @Value("${dome.operator.id}")
    private String DOME_OPERATOR_ID;

    @Autowired
    private TmfCachedDataRetriever tmfDataRetriever;

    public SubscriptionService() {
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    /**
     * 
     * Retrieves a subscription by its product ID.
     * 
     * @param productId The ID of the product to retrieve the subscription for.
     * @return The Subscription object if found, null otherwise.
     */
    public Subscription getSubscriptionByProductId(String productId) throws BadTmfDataException, ExternalServiceException {

        if (productId == null || productId.isEmpty()) {
            throw new BadTmfDataException("Product", productId, "Product ID cannot be null or empty");
        }

        logger.info("Fetching subscription from product id: {}", productId);

        Product prod;
        try {
            prod = this.tmfDataRetriever.getProduct(productId, null);
            if (prod == null) {
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve product {}: {}", productId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve product with ID: " + productId, e);
        }

        return RevenueProductMapper.toSubscription(prod);
    }

    /**
     * Retrieves all subscriptions associated with the DOME operator.
     * 
     * @return A list of Subscription objects.
     */
    public List<Subscription> getAllSubscriptions() throws ExternalServiceException, BadTmfDataException {
        logger.info("Fetching all active subscriptions (category = DOME OPERATOR Plan)...");

        List<Subscription> subscriptions = new ArrayList<>();

        tmfDataRetriever.fetchActiveProducts(50,
            product -> {
                // mapping Product -> Subscription
                Subscription sub = RevenueProductMapper.toSubscription(product);
                subscriptions.add(sub);
        });

        logger.info("Total active subscriptions retrieved: {}", subscriptions.size());
        return subscriptions;
    }

    /**
     * Retrieves a subscription by its related party ID.
     * 
     * @param id The ID of the related party to search for.
     * @return The Subscription object if found, null otherwise.
     */
    public Subscription getActiveSubscriptionByRelatedPartyId(String id) throws ExternalServiceException, BadTmfDataException {
        logger.debug("Retrieving active subscription by related party id: {}", id);

        if (id == null)
            return null;

        // FIXME: now assuming that a party can have only one active subscription
        for (Subscription sub : this.getAllSubscriptions()) {
            String status = sub.getStatus();
            logger.debug("Checking subscription {} with status {}", sub.getId(), sub.getStatus());

            if ("active".equalsIgnoreCase(status)
                    && RelatedPartyUtils.subscriptionHasPartyWithRole(sub, id, Role.BUYER)) {
                return sub;
            }
        }
        return null;
    }

    /**
     * Retrieves a list of subscriptions by related party ID and role.
     * 
     * @param id related party id
     * @param role role of related party
     */
    public List<Subscription> getSubscriptionsByRelatedPartyId(String id, Role role) throws ExternalServiceException, BadTmfDataException {
        return RelatedPartyUtils.retainSubscriptionsWithParty(this.getAllSubscriptions(), id, role, false);
    }

    /**
     * Retrieves all subscriptions associated with a specific plan ID.
     * 
     * @param id The ID of the plan to filter subscriptions by.
     * @return A list of Subscription objects that match the given plan ID.
     */
    public List<Subscription> getSubscriptionsByPlanId(String id) throws ExternalServiceException, BadTmfDataException {
        logger.debug("Retrieving subscriptions by plan ID: {}", id);
        return this.getAllSubscriptions().stream()
                .filter(sub -> sub.getPlan() != null && id.equals(sub.getPlan().getId()))
                .collect(Collectors.toList());
    }
}
