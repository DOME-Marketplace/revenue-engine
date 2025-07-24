package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RevenueItem {

    private String name;
    private Double value;
    private String currency;
    private OffsetDateTime chargeTime;
    
    private List<RevenueItem> items;

    public RevenueItem(String name, String currency) {
		this.name = name;
		this.currency = currency;
	}

    public RevenueItem() {
    	this.items = new ArrayList<>();
    }
    
    public RevenueItem(String name, Double value, String currency) {
        this.name = name;
        this.value = value; 
        this.currency = currency;
        this.items = new ArrayList<>();
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
        this.items.add(new RevenueItem(name, value, currency));
    }
 
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
    	this.name = name;
    }

    public Double getValue() {
        return value;
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

    /*
    public List<RevenueItem> clusterAccordingToChargeTime() {
        List<RevenueItem> out = new ArrayList<>();
        if(this.getItems()==null || this.getItems().isEmpty()) {
            out.add(this);
        } else {
            Map<OffsetDateTime, List<RevenueItem>> clusters = new HashMap<>();
            for(RevenueItem i:this.getItems()) {
                List<RevenueItem> ris = i.clusterAccordingToChargeTime();
                List<RevenueItem> cluster = clusters.get(ris.getC).clusterAccordingToChargeTime();
                out.addAll(i.clusterAccordingToChargeTime());
            }

        }
        return out;
    }
    */

}