package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.dome.brokerage.api.APIPartyApis;
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
	private final static String SERVICE_NAME = "Revenue Sharing Service";
	
    @Autowired
    private InvoicingService invoicingService;

    private final ProductCatalogManagementApis productCatalogManagementApis;
    private final CustomerManagementApis customerManagementApis;
    private final APIPartyApis apiPartyApis;
    private final ProductInventoryApis productInventoryApis;
    private final AppliedCustomerBillRateApis appliedCustomerBillRateApis;

    public HealthService(ProductCatalogManagementApis productCatalogManagementApis, 
    		CustomerManagementApis customerManagementApis,
    		APIPartyApis apiPartyApis, ProductInventoryApis productInventoryApis,
    		AppliedCustomerBillRateApis appliedCustomerBillRateApis) {
    	
    	this.productCatalogManagementApis = productCatalogManagementApis;
    	this.customerManagementApis = customerManagementApis;
		this.apiPartyApis = apiPartyApis;
		this.productInventoryApis = productInventoryApis;
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

	    // 2: check invoicing service
	    for(Check c: getInvoicingServiceCheck()) {
	    	health.addCheck(c);
	    	health.elevateStatus(c.getStatus());
        }

		// 3: check self info
		for(Check c: getChecksOnSelf()) {
			health.addCheck(c);
			health.elevateStatus(c.getStatus());
		}

		// 4: if self is ok, but overall status is FAIL, change it to WARN (not a local problem)
		boolean selfIsOk = health.getChecks("self", null).stream().allMatch(c -> c.getStatus() == HealthStatus.PASS);		
		if (selfIsOk && health.getStatus() == HealthStatus.FAIL) {
	        health.setStatus(HealthStatus.WARN);
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
	            ? SERVICE_NAME + " (ver. " + info.getVersion() + ")"
	            : SERVICE_NAME + " getInfo returned unexpected response";
	    
	    Check infoCheck = createCheck("self", "getInfo", "api", infoStatus, infoOutput);
	    out.add(infoCheck);

	    return out;
	}

    private List<Check> getInvoicingServiceCheck() {

        List<Check> out = new ArrayList<>();

        Check connectivity = createCheck("invoicing-service", "connectivity", "api");
        Check responsetime = createCheck("invoicing-service", "responseTime", "api");

		try {
			long start = System.currentTimeMillis();
        	Info invoicingInfo = invoicingService.getInfo();
			long end = System.currentTimeMillis();
            connectivity.setStatus(HealthStatus.PASS);
            connectivity.setOutput(toJson(invoicingInfo));
			responsetime.setOutput(""+(end-start)+"ms");
			responsetime.setStatus(HealthStatus.PASS);
	        out.add(responsetime);
        }
        catch(Exception e) {
            connectivity.setStatus(HealthStatus.FAIL);
            connectivity.setOutput(e.getMessage());
        }
		out.add(connectivity);

        return out;
    }

	private List<Check> getTMFChecks() {
		List<Check> out = new ArrayList<>();

		out.addAll(tmfCheck("tmf620-catalogManagement", () -> FetchUtils.streamAll(productCatalogManagementApis::listCatalogs, null, null, 1)));
		out.addAll(tmfCheck("tmf629-customerManagement", () -> FetchUtils.streamAll(customerManagementApis::listCustomers, null, null, 1)));
		out.addAll(tmfCheck("tmf632-partyManagement", () -> FetchUtils.streamAll(apiPartyApis::listOrganizations, null, null, 1)));
		out.addAll(tmfCheck("tmf637-productInventory", () -> FetchUtils.streamAll(productInventoryApis::listProducts, null, null, 1)));
		out.addAll(tmfCheck("tmf678-customerBillManagement", () -> FetchUtils.streamAll(appliedCustomerBillRateApis::listAppliedCustomerBillingRates, null, null, 1)));

		return out;
	}

	private List<Check> tmfCheck(String name, CheckedSupplier<Stream<?>> fetcher) {

		List<Check> checks = new ArrayList<>();

		Check connectivity = createCheck(name, "connectivity", "api");
		Check responsetime = createCheck(name, "responseTime", "api");

		try {
			long start = System.currentTimeMillis();
			fetcher.get().findAny();
			long end = System.currentTimeMillis();
			connectivity.setStatus(HealthStatus.PASS);
			checks.add(connectivity);
			responsetime.setOutput(""+(end-start)+"ms");
			responsetime.setStatus(HealthStatus.PASS);
			checks.add(responsetime);
		} catch (Exception e) {
			connectivity.setStatus(HealthStatus.FAIL);
			connectivity.setOutput(e.toString());
			checks.add(connectivity);
		}
		
		return checks;
	}

	@FunctionalInterface
	private interface CheckedSupplier<T> {
		T get() throws Exception;
	}
}


	
