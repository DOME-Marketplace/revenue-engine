package it.eng.dome.revenue.engine.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.dome.brokerage.api.APIPartyApis;
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

    private final APIPartyApis apiPartyApis;

    public HealthService(APIPartyApis apiPartyApis) {
		this.apiPartyApis = apiPartyApis;
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

		// 3: check self info
	    for(Check c: getChecksOnSelf()) {
	    	health.addCheck(c);
	    	health.elevateStatus(c.getStatus());
        }
		
	    // 4: check invoicing service
	    for(Check c: getInvoicingServiceCheck()) {
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
            connectivity.setOutput(new ObjectMapper().writeValueAsString(invoicingInfo));
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

		// TMF632
		Check tmf632 = createCheck("tmf-api", "connectivity", "tmf632");

		try {
			FetchUtils.streamAll(apiPartyApis::listOrganizations, null, null, 1).findAny();

			tmf632.setStatus(HealthStatus.PASS);

		} catch (Exception e) {
			tmf632.setStatus(HealthStatus.FAIL);
			tmf632.setOutput(e.toString());
		}

		out.add(tmf632);
		
        return out;
    }

}


	
