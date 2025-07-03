package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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
        // TODO: implement me
        return 0.0;
    }

    // implement retriever for key 'referred-providers-number'
    public Double computeReferredProvidersNumber(String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {
        // TODO: implement me
        return 0.0;
    }

    // implmenent retriever for key 'referred-providers-transaction-volume'
    public Double computeReferredProvidersTransactionVolume(String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {
        // TODO: implement me
        return 0.0;
    }

    public Double computeValueForKey(String key, String sellerId, OffsetDateTime from, OffsetDateTime to) throws Exception {
        switch (key) {
            case "bills-no-taxes":
                return computeBillsNoTaxes(sellerId, from, to);
            case "referred-providers-number":
                return computeReferredProvidersNumber(sellerId, from, to);
            case "referred-providers-transaction-volume":
                return computeReferredProvidersTransactionVolume(sellerId, from, to);
            default:
                throw new IllegalArgumentException("Unknown metric key: " + key);
        }
    }

}

