package it.eng.dome.revenue.engine.utils.health;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Check {

    private HealthStatus status;

    private String componentId;
    
    private String componentType;

    private List<String> affectedEndpoints;

    private OffsetDateTime time;

    private String output;

    public Check() {
        this.affectedEndpoints = new ArrayList<>();
    }

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(String componentId) {
        this.componentId = componentId;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public List<String> getAffectedEndpoints() {
        return affectedEndpoints;
    }

    public void addAffectedEndpoint(String affectedEndpoint) {
        this.affectedEndpoints.add(affectedEndpoint);
    }

    public OffsetDateTime getTime() {
        return time;
    }

    public void setTime(OffsetDateTime time) {
        this.time = time;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

}
