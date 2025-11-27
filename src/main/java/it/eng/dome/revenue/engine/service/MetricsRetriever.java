package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.eng.dome.revenue.engine.model.ComputeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
import it.eng.dome.revenue.engine.utils.ProductOfferingUtils;
import it.eng.dome.revenue.engine.utils.RelatedPartyUtils;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@Component(value = "metricsRetriever")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MetricsRetriever {

    private final Logger logger = LoggerFactory.getLogger(MetricsRetriever.class);

    @Autowired
    private TmfCachedDataRetriever tmfDataRetriever;

    public MetricsRetriever() {
    }

    /**
     * Computes the total billed amount excluding taxes between a seller and an optional buyer in a given period.
     *
     * @param sellerId the seller identifier
     * @param buyerId the buyer identifier (optional)
     * @param timePeriod the time period to consider
     * @return the total amount without taxes
     * @throws ExternalServiceException if bills cannot be retrieved
     * @throws IllegalArgumentException if sellerId is invalid
     */
    private Double computeBillsNoTaxes(String sellerId, String buyerId, TimePeriod timePeriod)
            throws ExternalServiceException {

        // TODO: when the new CB/ACBR approach will be done in the Billing Engine, retrieve CBs instead of ACBRs
        if (sellerId == null || sellerId.isEmpty()) {
            throw new IllegalArgumentException("Seller ID cannot be null or empty: " + sellerId);
        }

        try {
            // retrieve all seller billed invoices in the period
            List<CustomerBill> bills = tmfDataRetriever.retrieveCustomerBills(sellerId, buyerId, timePeriod);

            // sum taxExcludedAmount.value
            double totalAmountNoTaxes = 0.0;
            for (CustomerBill bill : bills) {
                if (bill.getTaxExcludedAmount() != null && bill.getTaxExcludedAmount().getValue() != null) {
                    totalAmountNoTaxes += bill.getTaxExcludedAmount().getValue();
                } else {
                    logger.debug("Bill {} contains no amount. Skipping it for the revenue computation", bill.getId());
                }
            }
            // totalAmountNoTaxes = 1256000.0;
            return totalAmountNoTaxes;
        } catch (Exception e) {
            logger.error("Failed to compute bills no taxes for seller {}: {}", sellerId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve bills for seller ID: " + sellerId, e);
        }
    }

    // TODO: test me
    /**
     * Retrieve the product offerings from the given seller (with role Seller) available in the given time period (validFor.startDate)
     * @param sellerId the seller identifier
     * @param timePeriod the time window to filter offerings
     * @return the list of published offerings
     * @throws ExternalServiceException if offerings cannot be retrieved
     */
    private List<ProductOffering> getPublishedOfferings(String sellerId, TimePeriod timePeriod) throws ExternalServiceException {
        if (sellerId == null || sellerId.isEmpty()) {
            throw new IllegalArgumentException("Seller ID cannot be null or empty: " + sellerId);
        }
        try {
            // filter on offerings valid in the given period
            Map<String, String> filter = Map.of("relatedParty.id", sellerId, "validFor.startDateTime.gt", timePeriod.getStartDateTime().toString(), "validFor.startDateTime.lt", timePeriod.getEndDateTime().toString());

            List<ProductOffering> publishedOfferings = new ArrayList<>();
            tmfDataRetriever.fetchProductOfferings(null, filter, 50, productOffering -> {
                    if(RelatedPartyUtils.offeringHasPartyWithRole(productOffering, sellerId, Role.SELLER))
                        publishedOfferings.add(productOffering);
                }
            );
            return publishedOfferings;

        } catch (Exception e) {
            logger.error("Failed to retrieve product offerings for seller {}: {}", sellerId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve product offerings for seller ID: " + sellerId, e);
        }
    }

    /**
     * Counts the published product offerings of a seller in the given period.
     *
     * @param sellerId the seller identifier
     * @param timePeriod the time window to consider
     * @return the number of published offerings
     * @throws ExternalServiceException if offerings cannot be retrieved
     */
    private Integer countPublishedOfferings(String sellerId, TimePeriod timePeriod) throws ExternalServiceException {
        List<ProductOffering> publishedOfferings = this.getPublishedOfferings(sellerId, timePeriod);
        return publishedOfferings.size();
    }

    /**
     * Counts the published self-service product offerings of a seller.
     *
     * @param sellerId the seller identifier
     * @param timePeriod the time period to consider
     * @return the number of published self-service offerings
     * @throws ExternalServiceException if offerings cannot be retrieved
     */
    private Integer countPublishedSelfserviceOfferings(String sellerId, TimePeriod timePeriod) throws ExternalServiceException {
        int count = 0;
        List<ProductOffering> publishedOfferings = this.getPublishedOfferings(sellerId, timePeriod);
        for(ProductOffering offering: publishedOfferings) {
            if(ProductOfferingUtils.isDomeManagingBilling(offering)
                    && ProductOfferingUtils.isDomeManagingPayment(offering)
                    && ProductOfferingUtils.isDomeManagingProcurement(offering)
                ) {
                count++;
            }
        }
        return count;
    }

    /**
     * Computes the number of providers referred by the given seller.
     *
     * @param sellerId the seller identifier
     * @return the number of referred providers
     * @throws BadTmfDataException if sellerId is invalid
     * @throws ExternalServiceException if referred providers cannot be retrieved
     */
    private Integer computeReferralsProvidersNumber(String sellerId/*, TimePeriod timePeriod*/)
            throws BadTmfDataException, ExternalServiceException {
        // retrieves the list of providers referenced by the seller
        if (sellerId == null || sellerId.isEmpty()) {
            throw new BadTmfDataException("Seller", sellerId, "Seller ID cannot be null or empty");
        }

        try {
            List<Organization> referred = tmfDataRetriever.listReferralsProviders(sellerId);

            Integer size = 0;

            if (referred != null && !referred.isEmpty()) {
                size = referred.size();
            }

            logger.info("Number of referred providers: {}", size);
            return size;
        } catch (Exception e) {
            logger.error("Failed to retrieve referred providers for seller {}: {}", sellerId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to retrieve referred providers for seller ID: " + sellerId, e);
        }
    }

    /**
     * Computes the total transaction volume of providers referred by the seller.
     *
     * @param sellerId the seller identifier
     * @param timePeriod the time period to consider
     * @return the total transaction volume of referred providers
     * @throws BadTmfDataException if sellerId is invalid
     * @throws ExternalServiceException if computation fails
     */
    private Double computeReferralsProvidersTransactionVolume(String sellerId, TimePeriod timePeriod)
            throws BadTmfDataException, ExternalServiceException {
        // retrieve the list of providers referred by the given seller
        if (sellerId == null || sellerId.isEmpty()) {
            throw new BadTmfDataException("Seller", sellerId, "Seller ID cannot be null or empty");
        }

        try {
            List<Organization> referred = tmfDataRetriever.listReferralsProviders(sellerId);
            Double totalTransactionVolume = 0.0;

            if (referred == null || referred.isEmpty()) { 
                return totalTransactionVolume;
            }

            // iterate over each referred provider
            for (Organization org : referred) {
                totalTransactionVolume += this.computeBillsNoTaxes(org.getId(), null, timePeriod);
            }
            logger.info("Total transaction volume for referred providers: {}", totalTransactionVolume);
            return totalTransactionVolume;	    
        } catch (Exception e) {
            logger.error("Failed to compute transaction volume for referred providers of seller {}: {}", sellerId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to compute transaction volume for seller ID: " + sellerId, e);
        }
    }

    /**
     * Computes the maximum transaction volume among providers referred by the seller.
     *
     * @param sellerId the seller identifier
     * @param timePeriod the period to consider
     * @return the maximum transaction volume
     * @throws BadTmfDataException if sellerId is invalid
     * @throws ExternalServiceException if computation fails
     */
    private Double computeReferralsProviderMaxTransactionVolume(String sellerId, TimePeriod timePeriod)
            throws BadTmfDataException, ExternalServiceException {
        // retrieve the list of providers referred by the given seller
        if (sellerId == null || sellerId.isEmpty()) {
            throw new BadTmfDataException("Seller", sellerId, "Seller ID cannot be null or empty");
        }

        try {
            List<Organization> referred = tmfDataRetriever.listReferralsProviders(sellerId);

            Double maxTransactionVolume = 0.0;

            if (referred == null || referred.isEmpty()) {
                return maxTransactionVolume;
            }

            // iterate over each referred provider
            for (Organization org : referred) {
                maxTransactionVolume = Math.max(maxTransactionVolume, this.computeBillsNoTaxes(org.getId(), null, timePeriod));
            }
            return maxTransactionVolume;
        } catch (Exception e) {
            logger.error("Failed to compute max transaction volume for referred providers of seller {}: {}", sellerId, e.getMessage(), e);
            throw new ExternalServiceException("Failed to compute max transaction volume for seller ID: " + sellerId, e);
        }
    }

    /**
     * Computes a metric identified by its key and contextual parameters.
     *
     * @param key the metric key
     * @param sellerId the seller identifier
     * @param buyerId the buyer identifier
     * @param timePeriod the time period to consider
     * @return the computed metric value
     * @throws BadTmfDataException if parameters are invalid
     * @throws ExternalServiceException if computation fails
     */
    public Double computeValueForKey(String key, String sellerId, String buyerId, TimePeriod timePeriod) throws BadTmfDataException, ExternalServiceException {
        ComputeMetric metric = ComputeMetric.fromKey(key);
        logger.debug("ComputeMetric: mapped key {} to metric {}", key, metric);
        switch (metric) {
            case BILLS_NO_TAXES:
                return computeBillsNoTaxes(sellerId, buyerId, timePeriod);
            case REFERRED_PROVIDERS_NUMBER:
                return (double) computeReferralsProvidersNumber(sellerId/*, timePeriod*/);
            case REFERRED_PROVIDERS_TRANSACTION_VOLUME:
                return computeReferralsProvidersTransactionVolume(sellerId, timePeriod);
            case REFERRED_PROVIDER_MAX_TRANSACTION_VOLUME:
                return computeReferralsProviderMaxTransactionVolume(sellerId, timePeriod);
            case PUBLISHED_PRODUCT_OFFERINGS:
                return this.countPublishedOfferings(sellerId, timePeriod).doubleValue();
            case PUBLISHED_SELFSERVICE_PRODUCT_OFFERINGS:
                return this.countPublishedSelfserviceOfferings(sellerId, timePeriod).doubleValue();
            default:
                throw new IllegalStateException("Unknown metric: " + metric);
        }
    }

    /**
     * Retrieves a distinct list of IDs associated with a given metric key.
     *
     * @param key the metric key
     * @param subscriberId the subscriber identifier
     * @param timePeriod the time period to consider
     * @return the list of distinct IDs
     * @throws BadTmfDataException if parameters are invalid
     * @throws ExternalServiceException if lookup fails
     */
    public List<String> getDistinctValuesForKey(String key, String subscriberId, TimePeriod timePeriod) throws BadTmfDataException, ExternalServiceException {
        switch(key) {
            case "activeSellersBehindMarketplace": {
                if (subscriberId == null || subscriberId.isEmpty()) {
                    throw new IllegalArgumentException("Subscriber ID cannot be null or empty: " + subscriberId);
                }
                List<Organization> orgs = this.getActiveSellersBehindMarketplace(subscriberId, timePeriod);
                List<String> orgIds = new ArrayList<>();
                for (Organization o : orgs) {
                    orgIds.add(o.getId());
                }
//                    orgIds.add(subscriberId); // also add the marketplace itself
                return orgIds;
            }
            case "billedSellersBehindMarketplace": {
                if (subscriberId == null || subscriberId.isEmpty()) {
                    throw new IllegalArgumentException("Subscriber ID cannot be null or empty: " + subscriberId);
                }
                List<Organization> orgs = this.listBilledSellersBehindMarketplace(subscriberId, timePeriod);
                List<String> orgIds = new ArrayList<>();
                for (Organization o : orgs)
                    orgIds.add(o.getId());
                return orgIds;
            }
                
            default:
                throw new IllegalArgumentException("Unknown metric key: " + key);
        }
    }

    /**
     * Retrieves the active sellers behind a federated marketplace.
     *
     * @param marketplaceId the marketplace identifier
     * @param timePeriod the time period to consider
     * @return the list of active sellers
     * @throws ExternalServiceException if retrieval fails
     * @throws BadTmfDataException if marketplaceId is invalid
     */
    private List<Organization> getActiveSellersBehindMarketplace(String marketplaceId, TimePeriod timePeriod) throws ExternalServiceException, BadTmfDataException {
            return this.tmfDataRetriever.listActiveSellersBehindFederatedMarketplace(marketplaceId, timePeriod);
    }

    /**
     * Retrieves the billed sellers behind a marketplace.
     *
     * @param marketplaceId the marketplace identifier
     * @param timePeriod the time period to consider
     * @return the list of billed sellers
     * @throws ExternalServiceException if retrieval fails
     * @throws BadTmfDataException if marketplaceId is invalid
     */
    private List<Organization> listBilledSellersBehindMarketplace(String marketplaceId, TimePeriod timePeriod) throws ExternalServiceException, BadTmfDataException {
        return this.tmfDataRetriever.listBilledSellersBehindMarketplace(marketplaceId, timePeriod);
    }

}
