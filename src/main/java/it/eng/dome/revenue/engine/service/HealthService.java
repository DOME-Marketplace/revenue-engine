package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.APIPartyApis;
import it.eng.dome.brokerage.api.AgreementManagementApis;
import it.eng.dome.brokerage.api.AppliedCustomerBillRateApis;
import it.eng.dome.brokerage.api.CustomerManagementApis;
import it.eng.dome.brokerage.api.ProductCatalogManagementApis;
import it.eng.dome.brokerage.api.ProductInventoryApis;
import it.eng.dome.brokerage.api.fetch.FetchUtils;
import it.eng.dome.brokerage.observability.AbstractHealthService;
import it.eng.dome.brokerage.observability.health.Check;
import it.eng.dome.brokerage.observability.health.Health;
import it.eng.dome.brokerage.observability.health.HealthStatus;
import it.eng.dome.brokerage.observability.info.Info;
import it.eng.dome.revenue.engine.invoicing.InvoicingService;



@Service
public class HealthService extends AbstractHealthService {
	
	private final Logger logger = LoggerFactory.getLogger(HealthService.class);
	private final static String SERVICE_NAME = "Invoicing Service";
	
    @Autowired
    private InvoicingService invoicingService;

    private final ProductCatalogManagementApis productCatalogManagementApis;
    private final CustomerManagementApis customerManagementApis;
    private final APIPartyApis apiPartyApis;
    private final ProductInventoryApis productInventoryApis;
    private final AgreementManagementApis agreementManagementApis;
    private final AppliedCustomerBillRateApis appliedCustomerBillRateApis;

    public HealthService(ProductCatalogManagementApis productCatalogManagementApis, 
    		CustomerManagementApis customerManagementApis,
    		APIPartyApis apiPartyApis, ProductInventoryApis productInventoryApis,
    		AgreementManagementApis agreementManagementApis,
    		AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
    	
    	this.productCatalogManagementApis = productCatalogManagementApis;
    	this.customerManagementApis = customerManagementApis;
		this.apiPartyApis = apiPartyApis;
		this.productInventoryApis = productInventoryApis;
		this.agreementManagementApis = agreementManagementApis;
		this.appliedCustomerBillRateApis = appliedCustomerBillRateApis;
	}
    
	@Override
	public Info getInfo() {

		Info info = super.getInfo();
		logger.debug("Response: {}", toJson(info));

		return info;
	}

    public Health getHealth() {
    	Health health = new Health();
		health.setDescription("Health for the " + SERVICE_NAME);

		health.elevateStatus(HealthStatus.PASS);

		// 1: check of the TMForum APIs dependencies
		for (Check c : getTMFChecks()) {
			health.addCheck(c);
			health.elevateStatus(c.getStatus());
		}

		// 2: check dependencies: in case of FAIL or WARN set it to WARN
		boolean onlyDependenciesFailing = health.getChecks("self", null).stream()
				.allMatch(c -> c.getStatus() == HealthStatus.PASS);
		
		if (onlyDependenciesFailing && health.getStatus() == HealthStatus.FAIL) {
	        health.setStatus(HealthStatus.WARN);
	    }

	    // 3: check invoicing service
	    for(Check c: getInvoicingServiceCheck()) {
	    	health.addCheck(c);
	    	health.elevateStatus(c.getStatus());
        }

		// 4: check self info
		for(Check c: getChecksOnSelf()) {
			health.addCheck(c);
			health.elevateStatus(c.getStatus());
		}

		// 5: build human-readable notes
	    health.setNotes(buildNotes(health));
		
		logger.debug("Health response: {}", toJson(health));
		
		return health;
    }

    private List<Check> getChecksOnSelf() {
	    List<Check> out = new ArrayList<>();

	    // Check getInfo API
	    Info info = getInfo();
	    HealthStatus infoStatus = (info != null) ? HealthStatus.PASS : HealthStatus.FAIL;
	    String infoOutput = (info != null)
	            ? SERVICE_NAME + " version: " + info.getVersion()
	            : SERVICE_NAME + " getInfo returned unexpected response";
	    
	    Check infoCheck = createCheck("self", "get-info", "api", infoStatus, infoOutput);
	    out.add(infoCheck);

	    return out;
	}

    private List<Check> getInvoicingServiceCheck() {

        List<Check> out = new ArrayList<>();

        Check connectivity = createCheck("invoicing-service", "connectivity", "external");

        try {
        	Info invoicingInfo = invoicingService.getInfo();
            connectivity.setStatus(HealthStatus.PASS);
            connectivity.setOutput(toJson(invoicingInfo));
        }
        catch(Exception e) {
            connectivity.setStatus(HealthStatus.WARN);
            connectivity.setOutput(e.getMessage());
        }
        out.add(connectivity);

        return out;
    }

	private List<Check> getTMFChecks() {
		List<Check> out = new ArrayList<>();

		out.add(tmfCheck("tmf620", () -> FetchUtils.streamAll(productCatalogManagementApis::listCatalogs, null, null, 1)));
		out.add(tmfCheck("tmf629", () -> FetchUtils.streamAll(customerManagementApis::listCustomers, null, null, 1)));
		out.add(tmfCheck("tmf632", () -> FetchUtils.streamAll(apiPartyApis::listOrganizations, null, null, 1)));
		out.add(tmfCheck("tmf637", () -> FetchUtils.streamAll(productInventoryApis::listProducts, null, null, 1)));
		out.add(tmfCheck("tmf651", () -> FetchUtils.streamAll(agreementManagementApis::listAgreements, null, null, 1)));
		out.add(tmfCheck("tmf678", () -> FetchUtils.streamAll(appliedCustomerBillRateApis::listAppliedCustomerBillingRates, null, null, 1)));

		return out;
	}

	private Check tmfCheck(String name, CheckedSupplier<Stream<?>> fetcher) {
		Check check = createCheck("tmf-api", "connectivity", name);

		try {
			fetcher.get().findAny();
			check.setStatus(HealthStatus.PASS);
		} catch (Exception e) {
			check.setStatus(HealthStatus.FAIL);
			check.setOutput(e.toString());
		}

		return check;
	}

	@FunctionalInterface
	private interface CheckedSupplier<T> {
		T get() throws Exception;
	}
}


	
