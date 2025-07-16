package it.eng.dome.revenue.engine.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RevenueItem {

    private String name;
    private Double value;
    private String currency;
    
    private List<RevenueItem> items;

 
    public RevenueItem(String name) {
		this.name = name;
		
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

    
    
	@Override
	public String toString() {
		return "RevenueItem [name=" + name + ", value=" + value + ", currency=" + currency + ", items=" + items + "]";
	}
}