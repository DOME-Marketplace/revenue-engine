package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionActive {
    
    private String name;
    
    @JsonProperty("subscriptionPlan") //@Valid ???????
    private SubscriptionPlan plan;
    
    private String status;
    private OffsetDateTime startDate;
    
    @JsonProperty("relatedParty") 
    private List<RelatedParty> relatedParties; 

    public SubscriptionActive() {}

    public SubscriptionActive(String name, SubscriptionPlan plan, List<RelatedParty> relatedParties, 
                             String status, OffsetDateTime startDate) {
        this.name = name;
        this.plan = plan;
        this.relatedParties = relatedParties;
        this.status = status;
        this.startDate = startDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public void setPlan(SubscriptionPlan plan) {
        this.plan = plan;
    }

    public List<RelatedParty> getRelatedParties() {
        return relatedParties;
    }

    public void setRelatedParties(List<RelatedParty> relatedParties) {
        this.relatedParties = relatedParties;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(OffsetDateTime startDate) {
        this.startDate = startDate;
    }

    @Override
    public String toString() {
        return "SubscriptionActive [name=" + name + ", plan=" + plan + ", relatedParties=" + 
               relatedParties + ", status=" + status + ", startDate=" + startDate + "]";
    }
}