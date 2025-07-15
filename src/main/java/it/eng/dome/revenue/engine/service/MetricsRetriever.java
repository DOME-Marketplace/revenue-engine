package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf632.v4.model.Organization;

@Component(value = "metricsRetriever")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MetricsRetriever implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(MetricsRetriever.class);

    @Autowired
    // Factory for TMF APIss
    private TmfDataRetriever tmfDataRetriever;

    @Override
    public void afterPropertiesSet() throws Exception {
//        this.billApi = new AppliedCustomerBillingRateApi(tmfApiFactory.getTMF678CustomerBillApiClient());
 //       this.orgApi = new OrganizationApi(tmfApiFactory.getTMF632PartyManagementApiClient());
 //       logger.info("TmfDataRetriever initialized with billApi and orgApi");
    }

    public MetricsRetriever() {
    }
    
    // implement retriever for key 'bills-no-taxes'
    public Double computeBillsNoTaxes(String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {

    	logger.info("Compute bills-no-taxes");
    	
    	// retrieve all seller invoices in the period
        List<AppliedCustomerBillingRate> bills = tmfDataRetriever.retrieveBills(sellerId, from, to);

        // sum taxExcludedAmount.value
        double totalAmountNoTaxes = 0.0;
        for (AppliedCustomerBillingRate bill : bills) {
            if (bill.getTaxExcludedAmount() != null && bill.getTaxExcludedAmount().getValue() != null) {
            	totalAmountNoTaxes += bill.getTaxExcludedAmount().getValue();
            } else {
                logger.warn("Bill {} contains no amount. Skipping it for the revenue computation", bill.getId());
           }
        }
        return totalAmountNoTaxes;
    }

    // implement retriever for key 'referred-providers-number'
    public Integer computeReferralsProvidersNumber(String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {
    	// TODO: test the method when listReferredProviders will work
    	// retrieves the list of providers referenced by the seller
        List<Organization> referred = tmfDataRetriever.listReferralsProviders(sellerId);

        if (referred == null) {
            return 0;
        }
        
        return referred.size();
    }

    // implement retriever for key 'referred-providers-transaction-volume'
    public Double computeReferralsProvidersTransactionVolume(String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {
    	// TODO: test the method when listReferredProviders will work
    	// retrieve the list of providers referred by the given seller
    	List<Organization> referred = tmfDataRetriever.listReferralsProviders(sellerId);

	    if (referred == null || referred.isEmpty()) 
	    	return 0.0;
	
	    double totalTransactionVolume = 0.0;
	    // iterate over each referred provider
	    for (Organization org : referred) {
			totalTransactionVolume += this.computeBillsNoTaxes(org.getId(), from, to);
			/*
	    	// retrieve all billing rates for this provider in the specified period
	        List<AppliedCustomerBillingRate> bills = tmfDataRetriever.retrieveBills(org.getId(), from, to);

	        // if there are any bills, sum the tax-excluded amounts
	        if (bills != null) {
	            for (AppliedCustomerBillingRate bill : bills) {
	                if (bill.getTaxExcludedAmount() != null && bill.getTaxExcludedAmount().getValue() != null) {
	                    totalAmountNoTaxes += bill.getTaxExcludedAmount().getValue();
	                } else {
	                    logger.warn("Bill {} contains no amount. Skipping it for the revenue computation", bill.getId());
	                }
	            }
	        }
			*/
	    }
	    return totalTransactionVolume;	    
    }
    
    // implement retriever for key 'referred-provider-max-transaction-volume'
    public Double computeReferralsProviderMaxTransactionVolume(String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {
    	// TODO: test the method when listReferredProviders will work
    	// retrieve the list of providers referred by the given seller
    	List<Organization> referred = tmfDataRetriever.listReferralsProviders(sellerId);

	    if (referred == null || referred.isEmpty()) 
	    	return 0.0;
	    
	    double maxTransactionVolume = 0.0;
	    
	    // iterate over each referred provider	    
	    for (Organization org : referred) {
			maxTransactionVolume = Math.max(maxTransactionVolume, this.computeBillsNoTaxes(org.getId(), from, to));
			/*
	        List<AppliedCustomerBillingRate> bills = tmfDataRetriever.retrieveBills(org.getId(), from, to);

	        double sum = 0.0;
	        // if there are any bills, sum the tax-excluded amounts
	        if (bills != null) {
	            for (AppliedCustomerBillingRate bill : bills) {
	                if (bill.getTaxExcludedAmount() != null && bill.getTaxExcludedAmount().getValue() != null) {
	                    sum += bill.getTaxExcludedAmount().getValue();
	                } else {
	                    logger.warn("Bill {} contains no amount. Skipping it for the revenue computation", bill.getId());
	                }
	            }
	        }

	        // keep track of the maximum billing amount among all referred providers
	        if (sum > transactionVolume) {
	            transactionVolume = sum;
	        }
			*/
	    }

	    return maxTransactionVolume;
    }

    public Double computeValueForKey(String key, String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {
    	switch (key) {
            case "bills-no-taxes":
                return computeBillsNoTaxes(sellerId, from, to);
            case "referrals-providers-number":
                return (double)computeReferralsProvidersNumber(sellerId, from, to);
            case "referrals-providers-transaction-volume":
                return computeReferralsProvidersTransactionVolume(sellerId, from, to);
            case "referrals-provider-max-transaction-volume":
                return computeReferralsProviderMaxTransactionVolume(sellerId, from, to);
            default:
                throw new IllegalArgumentException("Unknown metric key: " + key);
        }
    }

}

