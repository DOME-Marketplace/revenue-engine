package it.eng.dome.revenue.engine.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import it.eng.dome.brokerage.api.AgreementManagementApis;
import it.eng.dome.brokerage.api.CustomerManagementApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.tmforum.tmf629.v4.model.Customer;
import it.eng.dome.tmforum.tmf651.v4.model.Agreement;

@Component(value = "revenueService")
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RevenueService {

	private final Logger logger = LoggerFactory.getLogger(RevenueService.class);
	
	private CustomerManagementApis customerManagementApis;
	private AgreementManagementApis agreementManagementApis;
	
	public RevenueService(CustomerManagementApis customerManagementApis, AgreementManagementApis agreementManagementApis) {
		
		this.customerManagementApis = customerManagementApis;
		this.agreementManagementApis = agreementManagementApis;
	}
	
	public void getCustomers() {
		logger.info("Get getCustomers");
		List<Customer> customers = FetchUtils.streamAll(
			customerManagementApis::listCustomers,    // method reference
			null,                       		   // fields
			null,            					   // filter
			100                         	   // pageSize
		).toList();
		logger.info("Customers size: {}", customers.size());
		
		for (Customer customer : customers) {
			logger.info("ID: {}", customer.getId());
		}
	}
	
	public void getAgreements() {
		logger.info("Get getAgreements");
		List<Agreement> agreements = FetchUtils.streamAll(
			agreementManagementApis::listAgreements,    // method reference
			null,                       		   // fields
			null,            					   // filter
			100                         	   // pageSize
		).toList();
		logger.info("Agreement size: {}", agreements.size());
		
		for (Agreement agreement : agreements) {
			logger.info("ID: {}", agreement.getId());
		}
	}
}
