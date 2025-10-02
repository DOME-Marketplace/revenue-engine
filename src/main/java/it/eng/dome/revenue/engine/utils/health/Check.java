package it.eng.dome.revenue.engine.utils.health;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Check {

    private String componentName;

    private String measurementName;

    private HealthStatus status;

    private String componentId;
    
    private String componentType;

    private List<String> affectedEndpoints;

    private OffsetDateTime time;

    private String output;

    public Check() {
        this.affectedEndpoints = new ArrayList<>();
    }

    public Check(String componentName, String measurementName) {
        this();
        this.componentName = componentName;
        this.measurementName = measurementName;
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
        this.time = time.truncatedTo(ChronoUnit.SECONDS);
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

    @JsonIgnore
    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    @JsonIgnore
    public String getMeasurementName() {
        return measurementName;
    }

    public void setMeasurementName(String measurementName) {
        this.measurementName = measurementName;
    }
}
