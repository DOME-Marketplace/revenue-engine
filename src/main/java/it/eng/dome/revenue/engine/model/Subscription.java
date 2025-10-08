package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Subscription {
    
	private String id;

    private String href;
	
    private String name;
    
    @JsonProperty("plan")
    private Plan plan;
    
    private String status;
    private OffsetDateTime startDate;
    
    private Map<String,String> characteristics;
    private Product product;

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

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
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

    @JsonIgnore
    @Deprecated
    /**
     * @deprecated: Use getSubscriberId() instead.
     * @return
     */
    public String getBuyerId() {
        return this.getSubscriberId();
    }

    @JsonIgnore
    public String getSubscriberId() {
        if (relatedParties != null && !relatedParties.isEmpty()) {
            for (RelatedParty party : relatedParties) {
                if ("Buyer".equalsIgnoreCase(party.getRole())) {
                    return party.getId();
                }
            }
        }
        return null; // or throw an exception if a buyer is mandatory
    }

    public String getCharacteristics(String key) {
        if(characteristics != null) {
            return characteristics.get(key);
        }
        return null;
    }
    
	public Map<String, String> getCharacteristics() {
		return characteristics;
	}
    
    public void setCharacteristics(Map<String, String> characteristics) {
		this.characteristics = characteristics;
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

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

    public Set<TimePeriod> getChargePeriods() {
        SubscriptionTimeHelper timeHelper = new SubscriptionTimeHelper(this);
        return timeHelper.getChargePeriodTimes();
    }

    // TODO: create an new class BillCycle for this, instead of TimePeriod
    // the BillCycle should also contain the billdate and the payment due date

}