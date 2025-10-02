package it.eng.dome.revenue.engine.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    @Autowired
    private InvoicingService invoicingService;

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

        // TODO: implement proper/more checks and summarize the status in h.status

        for(Check c: this.getInvoicingServiceCheck())
            h.addCheck(c);

        for(Check c: this.getTMFChecks())
            h.addCheck(c);

        h.setStatus(HealthStatus.PASS);

        return h;
    }

    private List<Check> getInvoicingServiceCheck() {

        List<Check> out = new ArrayList<>();

        Check connectivity = new Check("invoicing", "connectivity");
        connectivity.setComponentType("external");
        connectivity.setTime(OffsetDateTime.now());

        try {
            Info invoicingInfo = invoicingService.getInfo();
            connectivity.setStatus(HealthStatus.PASS);
            connectivity.setOutput(invoicingInfo.getName()+":"+invoicingInfo.getVersion());
        }
        catch(Exception e) {
            connectivity.setStatus(HealthStatus.FAIL);
            connectivity.setOutput(e.getMessage());
        }
        out.add(connectivity);

        // TODO: return better/more checks

        return out;
    }

    private List<Check> getTMFChecks() {

        List<Check> out = new ArrayList<>();

        Check connectivity = new Check("tmf-api", "connectivity");
        connectivity.setComponentType("external");
        connectivity.setTime(OffsetDateTime.now());
        connectivity.setStatus(HealthStatus.FAIL);
        out.add(connectivity);

        // TODO: return better/more checks

        return out;
    }
}


	
