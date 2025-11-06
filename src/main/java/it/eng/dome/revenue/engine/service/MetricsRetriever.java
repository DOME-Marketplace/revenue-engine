package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.revenue.engine.exception.BadTmfDataException;
import it.eng.dome.revenue.engine.exception.ExternalServiceException;
import it.eng.dome.revenue.engine.service.cached.TmfCachedDataRetriever;
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
    
    // implement retriever for key 'bills-no-taxes'
    private Double computeBillsNoTaxes(String sellerId, String buyerId, TimePeriod timePeriod)
            throws BadTmfDataException, ExternalServiceException {

        // TODO: when the new CB/ACBR approach will be done in the Billing Engine, retrieve CBs instead of ACBRs
        if (sellerId == null || sellerId.isEmpty()) {
            // FIXME: wrong exception type
            throw new BadTmfDataException("Seller", sellerId, "Seller ID cannot be null or empty");
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

    // implement retriever for key 'referred-providers-number'
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

    // implement retriever for key 'referred-providers-transaction-volume'
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

    // implement retriever for key 'referred-provider-max-transaction-volume'
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

    public Double computeValueForKey(String key, String sellerId, String buyerId, TimePeriod timePeriod) throws BadTmfDataException, ExternalServiceException {
        switch (key) {
            case "bills-no-taxes":
                return computeBillsNoTaxes(sellerId, buyerId, timePeriod);
            case "referred-providers-number":
                return (double) computeReferralsProvidersNumber(sellerId/*, timePeriod*/);
            case "referred-providers-transaction-volume":
                return computeReferralsProvidersTransactionVolume(sellerId, timePeriod);
            case "referred-provider-max-transaction-volume":
                return computeReferralsProviderMaxTransactionVolume(sellerId, timePeriod);
            default:
                // FIXME: wrong exception type
                throw new BadTmfDataException("Metric", key, "Unknown metric key");
        }
    }

    public List<String> getDistinctValuesForKey(String key, String subscriberId, TimePeriod timePeriod)
            throws BadTmfDataException, ExternalServiceException {
        switch(key) {
            case "activeSellersBehindMarketplace":
                if (subscriberId == null || subscriberId.isEmpty()) {
                    // FIXME: wrong exception
                    throw new BadTmfDataException("Subscriber", subscriberId, "Subscriber ID cannot be null or empty");
                }
                try {
                    List<Organization> orgs = this.getActiveSellersBehindMarketplace(subscriberId, timePeriod);
                    List<String> orgIds = new ArrayList<>();
                    for(Organization o: orgs) {
                        orgIds.add(o.getId());
                    }
//                    orgIds.add(subscriberId); // also add the marketplace itself
                    return orgIds;
                } catch (Exception e) {
                    logger.error("Failed to retrieve active sellers behind marketplace {}: {}", subscriberId, e.getMessage(), e);
                    // FIXME: this is not the correct exception
                    throw new ExternalServiceException("Failed to retrieve active sellers behind marketplace ID: " + subscriberId, e);
                }
            case "billedSellersBehindMarketplace":
                if (subscriberId == null || subscriberId.isEmpty()) {
                    throw new BadTmfDataException("Subscriber", subscriberId, "Subscriber ID cannot be null or empty");
                }
                List<Organization> orgs = this.listBilledSellersBehindMarketplace(subscriberId, timePeriod);
                List<String> orgIds = new ArrayList<>();
                for(Organization o: orgs)
                    orgIds.add(o.getId());
                return orgIds;
                
            default:
                // FIXME: this is not the correct exception
                throw new BadTmfDataException("Metric", key, "Unknown metric key");
        }

    }

    private List<Organization> getActiveSellersBehindMarketplace(String marketplaceId, TimePeriod timePeriod) throws ExternalServiceException, BadTmfDataException {
            return this.tmfDataRetriever.listActiveSellersBehindFederatedMarketplace(marketplaceId, timePeriod);
    }

    private List<Organization> listBilledSellersBehindMarketplace(String marketplaceId, TimePeriod timePeriod) throws ExternalServiceException, BadTmfDataException {
        return this.tmfDataRetriever.listBilledSellersBehindMarketplace(marketplaceId, timePeriod);
    }

}
