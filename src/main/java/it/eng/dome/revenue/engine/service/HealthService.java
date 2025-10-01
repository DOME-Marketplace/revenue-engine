package it.eng.dome.revenue.engine.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import it.eng.dome.revenue.engine.invoicing.InvoicingService;
import it.eng.dome.revenue.engine.utils.health.Check;
import it.eng.dome.revenue.engine.utils.health.Health;
import it.eng.dome.revenue.engine.utils.health.HealthStatus;
import it.eng.dome.revenue.engine.utils.health.Info;


@Service
public class HealthService implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(BillsService.class);

    @Autowired
    InvoicingService invoicingService;

    @Autowired
    private BuildProperties buildProperties;

    @Override
    public void afterPropertiesSet() throws Exception {
        // create an API to the invoicing service
        // create an API for each of the used TMF APIs ... or just one?
    }

    public Info getInfo() {
        Info info = new Info();

        info.setName(buildProperties.getName());
        
        info.setVersion(buildProperties.getVersion());

    	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        ZonedDateTime zonedDateTime = buildProperties.getTime().atZone(ZoneId.of("Europe/Rome"));
        info.setReleaseTime(zonedDateTime.format(formatter));

        return info;
    }

    public Health getHealth() {
        Health h = new Health();
        h.setDescription("health for the revenue engine service");

        // TODO: implement proper checks and summarize the status in h.status

        for(Entry<String, Check> e: this.getInvoicingServiceCheck().entrySet())
            h.addCheck(e.getKey(), e.getValue());

        for(Entry<String, Check> e: this.getTMFChecks().entrySet())
            h.addCheck(e.getKey(), e.getValue());

        h.setStatus(HealthStatus.PASS);

        return h;
    }

    private Map<String, Check> getInvoicingServiceCheck() {
        // TODO: return better/more checks

        Check connectivity = new Check();
        connectivity.setComponentType("external");
        connectivity.setTime(OffsetDateTime.now());

        if("PASS".equalsIgnoreCase(invoicingService.info())) {
            connectivity.setStatus(HealthStatus.PASS);
        } else {
            connectivity.setStatus(HealthStatus.FAIL);
        }

        Map<String, Check> out = new HashMap<>();
        out.put("invoicing-service:connections", connectivity);

        return out;
    }

    private Map<String, Check> getTMFChecks() {
        // TODO: return better/more checks

        Check connectivity = new Check();
        connectivity.setComponentType("external");
        connectivity.setTime(OffsetDateTime.now());
        connectivity.setStatus(HealthStatus.FAIL);

        Map<String, Check> out = new HashMap<>();
        out.put("tmf-api:connections", connectivity);

        return out;
    }
}


	
