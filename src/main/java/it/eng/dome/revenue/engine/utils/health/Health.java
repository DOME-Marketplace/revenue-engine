package it.eng.dome.revenue.engine.utils.health;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Health {

    private HealthStatus status;
    private String releaseId;

    private List<String> notes;
    private String output;
    private String description;

    private Map<String, List<Check>> checks;

    public Health() {
        this.checks = new HashMap<>();
        this.notes = new ArrayList<>();
    }

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public void addCheck(Check check) {
        String key = check.getComponentName()+":"+check.getMeasurementName();
        if(this.checks.get(key)==null)
            this.checks.put(key, new ArrayList<>());
        this.checks.get(key).add(check);
    }

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId;
    }

    public List<String> getNotes() {
        return notes;
    }

    public void setNotes(List<String> notes) {
        this.notes = notes;
    }

    public void addNote(String note) {
        this.notes.add(note);
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }    

    public Map<String, List<Check>> getChecks() {
        return checks;
    }

}
