package it.eng.dome.revenue.engine.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RevenueItem {

    private String name;
    private Double value;
    private String currency;
    private String computationBase;
    private Double computationBaseValue;
    
    private List<RevenueItem> items;

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

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
		this.value = value;
	}
    
    public Double getOverallValue() {
        Double total = this.value;
        for (RevenueItem item : items) {
            total += item.getOverallValue();
        }
        return total;
    }

    public String getCurrency() {
        return currency;
    }

        public String getComputationBase() {
        return computationBase;
    }

    public void setComputationBase(String computationBase) {
        this.computationBase = computationBase;
    }

    public Double getComputationBaseValue() {
        return computationBaseValue;
    }

    public void setComputationBaseValue(Double computationBaseValue) {
        this.computationBaseValue = computationBaseValue;
    }

}
