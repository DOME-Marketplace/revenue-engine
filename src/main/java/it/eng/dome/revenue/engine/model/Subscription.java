package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Subscription {
    
	private String id;
	
    private String name;
    
    @JsonProperty("subscriptionPlan") //@Valid ???????
    private Plan plan;
    
    private String status;
    private OffsetDateTime startDate;
    
    @JsonProperty("relatedParty") 
    private List<RelatedParty> relatedParties; 

    public Subscription() {}


    public Subscription(String id, String name, Plan plan, String status, OffsetDateTime startDate,
			List<RelatedParty> relatedParties) {
		this.id = id;
		this.name = name;
		this.plan = plan;
		this.status = status;
		this.startDate = startDate;
		this.relatedParties = relatedParties;
	}
    
    public String getId() {
		return id;
	}
    
    public void setId(String id) {
    	this.id = id;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
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

    public String getBuyerId() {
        if (relatedParties != null && !relatedParties.isEmpty()) {
            for (RelatedParty party : relatedParties) {
                if ("Buyer".equalsIgnoreCase(party.getRole())) {
                    return party.getId();
                }
            }
        }
        return null; // or throw an exception if a buyer is mandatory
    }

    @Override
    public String toString() {
		return "Subscription{" +
				"id='" + id + '\'' +
				", name='" + name + '\'' +
				", plan=" + plan +
				", status='" + status + '\'' +
				", startDate=" + startDate +
				", relatedParties=" + relatedParties +
				'}';
	}
}