package it.eng.dome.revenue.engine.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.brokerage.api.AgreementManagementApis;
import it.eng.dome.brokerage.api.CustomerManagementApis;
import it.eng.dome.revenue.engine.mapper.RevenueBillingMapper;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;
import it.eng.dome.tmforum.tmf629.v4.model.Customer;
import it.eng.dome.tmforum.tmf651.v4.model.Agreement;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;


@Component(value = "revenueService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RevenueService implements InitializingBean {

	private final Logger logger = LoggerFactory.getLogger(RevenueService.class);
	
	@Autowired
	private TmfApiFactory tmfApiFactory; 
	
	@Autowired
	private TmfDataRetriever tmfDataRetriever; 
	
	private CustomerManagementApis customer;
	private AgreementManagementApis agreement;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		customer = new CustomerManagementApis(tmfApiFactory.getTMF629CustomerManagementApiClient());
		agreement = new AgreementManagementApis(tmfApiFactory.getTMF651AgreementManagementApiClient());
	}
	
	public void getCustomers() {
		logger.info("Get getCustomers");
		List<Customer> customers = customer.getAllCustomers(null, null);
		logger.info("Customers size: {}", customers.size());
		
		for (Customer customer : customers) {
			logger.info("ID: {}", customer.getId());
		}
	}
	
	public void getAgreements() {
		logger.info("Get getAgreements");
		List<Agreement> agreements = agreement.getAllAgreements(null, null);
		logger.info("Agreement size: {}", agreements.size());
		
		for (Agreement agreement : agreements) {
			logger.info("ID: {}", agreement.getId());
		}
	}	
	
	/**
	 * Builds an AppliedCustomerBillingRate from a RevenueStatement.
	 * 
	 * @param rs The RevenueStatement to convert.
	 * @return An AppliedCustomerBillingRate object.
	 * @throws IllegalArgumentException if the RevenueStatement is null or does not contain related party information.
	 */
	
	public AppliedCustomerBillingRate buildACBR(RevenueStatement rs) {
        if (rs == null || rs.getSubscription() == null ||
            rs.getSubscription().getRelatedParties() == null ||
            rs.getSubscription().getRelatedParties().isEmpty()) {
            throw new IllegalArgumentException("Missing related party information in RevenueStatement");
        }
        //TODO: check which rl to retrieve 
        String relatedPartyId = rs.getSubscription().getRelatedParties().get(0).getId();
        BillingAccountRef billingAccountRef = tmfDataRetriever.retrieveBillingAccountByRelatedPartyId(relatedPartyId);
        return RevenueBillingMapper.toACBR(rs, billingAccountRef);
    }
}
