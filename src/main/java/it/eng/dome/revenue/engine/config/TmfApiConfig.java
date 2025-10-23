package it.eng.dome.revenue.engine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.eng.dome.brokerage.api.APIPartyApis;
import it.eng.dome.brokerage.api.AgreementManagementApis;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerBillApis;
import it.eng.dome.brokerage.api.CustomerManagementApis;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;
import it.eng.dome.revenue.engine.tmf.TmfApiFactory;

@Configuration
public class TmfApiConfig {

	private final Logger logger = LoggerFactory.getLogger(TmfApiConfig.class);

	@Autowired
	private TmfApiFactory tmfApiFactory;

	@Bean
	public ProductCatalogManagementApis productCatalogManagementApis() {
		logger.info("Initializing of ProductCatalogManagementApis");

		return new ProductCatalogManagementApis(tmfApiFactory.getTMF620ProductCatalogManagementApiClient());
	}

	@Bean
	public CustomerManagementApis customerManagementApis() {
		logger.info("Initializing of CustomerManagementApis");

		return new CustomerManagementApis(tmfApiFactory.getTMF629CustomerManagementApiClient());
	}

	@Bean
	public APIPartyApis apiPartyApis() {
		logger.info("Initializing of APIPartyApis");

		return new APIPartyApis(tmfApiFactory.getTMF632PartyManagementApiClient());
	}

	@Bean
	public ProductInventoryApis productInventoryApis() {
		logger.info("Initializing of ProductInventoryApis");

		return new ProductInventoryApis(tmfApiFactory.getTMF637ProductInventoryApiClient());
	}

	@Bean
	public AgreementManagementApis agreementManagementApis() {
		logger.info("Initializing of AgreementManagementApis");

		return new AgreementManagementApis(tmfApiFactory.getTMF651AgreementManagementApiClient());
	}

	@Bean
	public AppliedCustomerBillRateApis appliedCustomerBillRateApis() {
		logger.info("Initializing of AppliedCustomerBillRateApis");

		return new AppliedCustomerBillRateApis(tmfApiFactory.getTMF678CustomerBillApiClient());
	}

	@Bean
	public CustomerBillApis customerBillApis() {
		logger.info("Initializing of CustomerBillApis");

		return new CustomerBillApis(tmfApiFactory.getTMF678CustomerBillApiClient());
	}
}
