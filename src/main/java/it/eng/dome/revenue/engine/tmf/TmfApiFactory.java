package it.eng.dome.revenue.engine.tmf;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;


import it.eng.dome.brokerage.billing.utils.UrlPathUtils;

@Component(value = "tmfApiFactory")
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public final class TmfApiFactory implements InitializingBean {
	
	private static final Logger log = LoggerFactory.getLogger(TmfApiFactory.class);
	private static final String TMF_ENDPOINT_CONCAT_PATH = "-";
	
    @Value("${tmforumapi.tmf_endpoint}")
    public String tmfEndpoint;
    
    @Value("${tmforumapi.tmf_envoy}")
    public boolean tmfEnvoy;
    
    @Value("${tmforumapi.tmf_namespace}")
    public String tmfNamespace;
    
    @Value("${tmforumapi.tmf_postfix}")
    public String tmfPostfix;    
    
    @Value("${tmforumapi.tmf_port}")
    public String tmfPort;
	
    @Value( "${tmforumapi.tmf629_customer_management_path}" )
	private String tmf629CustomerManagementPath;

    @Value( "${tmforumapi.tmf632_party_management_path}" )
	private String tmf632PartyManagementPath;
    
	@Value( "${tmforumapi.tmf637_inventory_path}" )
	private String tmf637ProductInventoryPath;

    @Value( "${tmforumapi.tmf651_agreement_management_path}" )
	private String tmf651AgreementManagementPath;
    
	@Value( "${tmforumapi.tmf678_billing_path}" )
	private String tmf678CustomerBillPath;
	
	private it.eng.dome.tmforum.tmf629.v4.ApiClient apiClientTmf629;
	private it.eng.dome.tmforum.tmf632.v4.ApiClient apiClientTmf632;
	private it.eng.dome.tmforum.tmf637.v4.ApiClient apiClientTmf637;
	private it.eng.dome.tmforum.tmf651.v4.ApiClient apiClientTmf651;
	private it.eng.dome.tmforum.tmf678.v4.ApiClient apiClientTmf678;
	

	public it.eng.dome.tmforum.tmf629.v4.ApiClient getTMF629CustomerManagementApiClient() {	
		if (apiClientTmf629 == null) {
			apiClientTmf629  = it.eng.dome.tmforum.tmf629.v4.Configuration.getDefaultApiClient();
			
			String basePath = tmfEndpoint;
			if (!tmfEnvoy) { // no envoy specific path
				basePath += TMF_ENDPOINT_CONCAT_PATH + "customer-management" + "." + tmfNamespace + "." + tmfPostfix + ":" + tmfPort;
			}
			
			apiClientTmf629.setBasePath(basePath + "/" + tmf629CustomerManagementPath);
			log.debug("Invoke Catalog API at endpoint: " + apiClientTmf629.getBasePath());
		}
		
		return apiClientTmf629;
	}

	public it.eng.dome.tmforum.tmf632.v4.ApiClient getTMF632PartyManagementApiClient() {	
		if (apiClientTmf632 == null) {
			apiClientTmf632  = it.eng.dome.tmforum.tmf632.v4.Configuration.getDefaultApiClient();
			
			String basePath = tmfEndpoint;
			if (!tmfEnvoy) { // no envoy specific path
				basePath += TMF_ENDPOINT_CONCAT_PATH + "party-catalog" + "." + tmfNamespace + "." + tmfPostfix + ":" + tmfPort;
			}
			
			apiClientTmf632.setBasePath(basePath + "/" + tmf629CustomerManagementPath);
			log.debug("Invoke Customer API at endpoint: " + apiClientTmf632.getBasePath());
		}
		
		return apiClientTmf632;
	}
	
	public it.eng.dome.tmforum.tmf637.v4.ApiClient getTMF637ProductInventoryApiClient() {	
		if (apiClientTmf637 == null) {
			apiClientTmf637  = it.eng.dome.tmforum.tmf637.v4.Configuration.getDefaultApiClient();
			
			String basePath = tmfEndpoint;
			if (!tmfEnvoy) { // no envoy specific path
				basePath += TMF_ENDPOINT_CONCAT_PATH + "product-inventory" + "." + tmfNamespace + "." + tmfPostfix + ":" + tmfPort;
			}
			
			apiClientTmf637.setBasePath(basePath + "/" + tmf637ProductInventoryPath);
			log.debug("Invoke Product Inventory API at endpoint: " + apiClientTmf637.getBasePath());
		}
		
		return apiClientTmf637;
	}
	
	public it.eng.dome.tmforum.tmf651.v4.ApiClient getTMF651AgreementManagementApiClient() {	
		if (apiClientTmf651 == null) {
			apiClientTmf651  = it.eng.dome.tmforum.tmf651.v4.Configuration.getDefaultApiClient();
			
			String basePath = tmfEndpoint;
			if (!tmfEnvoy) { // no envoy specific path
				basePath += TMF_ENDPOINT_CONCAT_PATH + "agreement-management" + "." + tmfNamespace + "." + tmfPostfix + ":" + tmfPort;
			}
			
			apiClientTmf651.setBasePath(basePath + "/" + tmf651AgreementManagementPath);
			log.debug("Invoke Agreement API at endpoint: " + apiClientTmf651.getBasePath());
		}
		
		return apiClientTmf651;
	}
	
	public it.eng.dome.tmforum.tmf678.v4.ApiClient getTMF678CustomerBillApiClient() {
		if (apiClientTmf678 == null) { 
			apiClientTmf678 = it.eng.dome.tmforum.tmf678.v4.Configuration.getDefaultApiClient();
			
			String basePath = tmfEndpoint;
			if (!tmfEnvoy) { // no envoy specific path
				basePath += TMF_ENDPOINT_CONCAT_PATH + "customer-bill-management" + "." + tmfNamespace + "." + tmfPostfix + ":" + tmfPort;
			}
			
			apiClientTmf678.setBasePath(basePath + "/" + tmf678CustomerBillPath);
			log.debug("Invoke Customer Billing API at endpoint: " + apiClientTmf678.getBasePath());
		}
		return apiClientTmf678;
	}


		
	@Override
	public void afterPropertiesSet() throws Exception {
		
		log.info("Revenue Engine is using the following TMForum endpoint prefix: " + tmfEndpoint);	
		if (tmfEnvoy) {
			log.info("You set the apiProxy for TMForum endpoint. No tmf_port {} can be applied", tmfPort);	
		} else {
			log.info("No apiProxy set for TMForum APIs. You have to access on specific software via paths at tmf_port {}", tmfPort);	
		}
		
		Assert.state(!StringUtils.isBlank(tmfEndpoint), "Revenue Engine not properly configured. tmf620_catalog_base property has no value.");
		Assert.state(!StringUtils.isBlank(tmf629CustomerManagementPath), "Revenue Engine not properly configured. tmf629_customer_management_path property has no value.");
		Assert.state(!StringUtils.isBlank(tmf632PartyManagementPath), "Revenue Engine not properly configured. tmf632_party_management_path property has no value.");
		Assert.state(!StringUtils.isBlank(tmf637ProductInventoryPath), "Revenue Engine not properly configured. tmf637_inventory_path property has no value.");
		Assert.state(!StringUtils.isBlank(tmf651AgreementManagementPath), "Revenue Engine not properly configured. tmf651_agreement_management_path property has no value.");
		Assert.state(!StringUtils.isBlank(tmf678CustomerBillPath), "Revenue Engine not properly configured. tmf678_billing_path property has no value.");
			
		if (tmfEndpoint.endsWith("/")) {
			tmfEndpoint = UrlPathUtils.removeFinalSlash(tmfEndpoint);		
		}
		
		if (tmf629CustomerManagementPath.startsWith("/")) {
			tmf629CustomerManagementPath = UrlPathUtils.removeInitialSlash(tmf629CustomerManagementPath);
		}
		
		if (tmf632PartyManagementPath.startsWith("/")) {
			tmf632PartyManagementPath = UrlPathUtils.removeInitialSlash(tmf632PartyManagementPath);
		}
				
		if (tmf637ProductInventoryPath.startsWith("/")) {
			tmf637ProductInventoryPath = UrlPathUtils.removeInitialSlash(tmf637ProductInventoryPath);
		}
		
		if (tmf651AgreementManagementPath.startsWith("/")) {
			tmf651AgreementManagementPath = UrlPathUtils.removeInitialSlash(tmf651AgreementManagementPath);
		}
		
		if (tmf678CustomerBillPath.startsWith("/")) {
			tmf678CustomerBillPath = UrlPathUtils.removeInitialSlash(tmf678CustomerBillPath);
		}
	}
		
}
