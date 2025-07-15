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
    private BundleOperator bundleOp;
    private List<RevenueItem> items;

    public RevenueItem(String name, Double value, String currency) {
        this.name = name;
        this.value = value;
        this.currency = currency;
        this.items = new ArrayList<>();
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

    public List<RevenueItem> getItems() {
        return items;
    }

    public void setItems(List<RevenueItem> items) {
        this.items = (items != null) ? new ArrayList<>(items) : new ArrayList<>();
    }

    public void addRevenueItem(String name, Double value, String currency) {
        if (currency != null && !currency.equals(this.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " vs " + currency);
        }
        this.items.add(new RevenueItem(name, value, currency));
    }

    public BundleOperator getBundleOp() {
        return bundleOp;
    }

    public void setBundleOp(BundleOperator bundleOp) {
        this.bundleOp = bundleOp;
    }

    public Double getOverallValue() {
        if (items == null || items.isEmpty()) {
            return value;
        }

        switch (bundleOp != null ? bundleOp : BundleOperator.CUMULATIVE) {
            case ALTERNATIVE_HIGHER:
                return items.stream()
                    .mapToDouble(RevenueItem::getOverallValue)
                    .min()
                    .orElse(0.0);

            case ALTERNATIVE_LOWER:
                return items.stream()
                    .mapToDouble(RevenueItem::getOverallValue)
                    .max()
                    .orElse(0.0); 

            case CUMULATIVE:
            default:
                return value + items.stream()
                    .mapToDouble(RevenueItem::getOverallValue)
                    .sum();
        }
    }

}
