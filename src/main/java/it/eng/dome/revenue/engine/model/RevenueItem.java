package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RevenueItem {

    private String name;
    private Double value;
    private String currency;
    private Boolean estimated;

    private String type;

    private OffsetDateTime chargeTime;
    
    private List<RevenueItem> items;

    public RevenueItem() {
    	this.items = new ArrayList<>();
    }

    public RevenueItem(String name, String currency) {
        this();
		this.name = name;
		this.currency = currency;
	}
    
    public RevenueItem(String name, Double value, String currency) {
        this(name, currency);
        this.value = value; 
    }

    public List<RevenueItem> getItems() {
        return items;
    }

    public void setItems(List<RevenueItem> items) {
        this.items = (items != null) ? new ArrayList<>(items) : new ArrayList<>();
    }

    public void addRevenueItem(String name, Double value, String currency) {
        if(currency!=null && !currency.equals(this.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " vs " + currency);
        }
        this.addRevenueItem(new RevenueItem(name, value, currency));
    }

    public void addRevenueItem(RevenueItem item) {
        this.items.add(item);
    }
 
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
    	this.name = name;
    }

    public Double getValue() {
        return (value!=null ? value : 0);
    }

    public void setValue(Double value) {
    	this.value = value;
    }
    
    public Double getOverallValue() {
        double total = (this.value != null) ? this.value : 0.0;
        for (RevenueItem item : items) {
            Double childValue = item.getOverallValue();
            if (childValue != null) {
                total += childValue;
            }
        }
        return total;
    }

    public void setCurrency(String currency) {
		this.currency = currency;
	} 
    
    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getChargeTime() {
        return chargeTime;
    }

    public void setChargeTime(OffsetDateTime chargeTime) {
        this.chargeTime = chargeTime;
    }    
    
	@Override
	public String toString() {
		return "RevenueItem [name=" + name + ", value=" + value + ", currency=" + currency + ", items=" + items + "]";
	}

    public Set<OffsetDateTime> extractChargeTimes() {
        Set<OffsetDateTime> out = new TreeSet<>();
        if(this.getItems()==null || this.getItems().isEmpty()) {
            if(this.getChargeTime()!=null)
                out.add(this.getChargeTime());
        } else {
            for(RevenueItem i:this.getItems()) {
                out.addAll(i.extractChargeTimes());
            }
        }
        return out;
    }

    /**
     * Builds a new revenueitem only including the items (recursively) with the same chargeTime
     */
    public RevenueItem getFilteredClone(OffsetDateTime chargeTime) {
        RevenueItem clone = new RevenueItem(this.name, this.value, this.currency);
        clone.setChargeTime(chargeTime);
        clone.setEstimated(this.estimated);
        clone.setType(this.type);
        if (this.items != null) {
            for (RevenueItem item : this.items) {
                if(item.getChargeTime() == null || item.getChargeTime().equals(chargeTime))
                    clone.getItems().add(item.getFilteredClone(chargeTime));
            }
        }
        return clone;
    }

    @JsonProperty("estimated")
    public Boolean isEstimated() {
        if(this.items!=null) {
            for(RevenueItem i:items) {
                if(i.isEstimated())
                    return true;
            }
        }
        return Boolean.TRUE.equals(this.estimated);
    }

    public void setEstimated(Boolean estimated) {
        this.estimated = estimated;
    }

    public String getType() {
        if(this.items!=null) {
            for(RevenueItem i:items) {
                if(i.getType()!=null)
                    return i.getType();
            }
        }
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

}