package it.eng.dome.revenue.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Discount {
	
    @JsonProperty("isBundle")
    private Boolean isBundle;
    
    @JsonProperty("bundleOp")
    private String bundleOp;
    
    @JsonProperty("discounts")
    private List<Discount> discounts;
    
    @JsonProperty("computationBase")
    private String computationBase;
    
    @JsonProperty("referencePeriod")
    private String referencePeriod;
    
    @JsonProperty("applicableBaseRange")
    private Range applicableBaseRange;
    
    @PositiveOrZero
    @JsonProperty("percent")
    private Double percent;
    
    @PositiveOrZero
    @JsonProperty("amount")
    private Double amount;

    public Discount() {}

    // Getter e Setter completi
    public Boolean getIsBundle() {
        return isBundle;
    }

    public void setIsBundle(Boolean isBundle) {
        this.isBundle = isBundle;
    }

    public String getBundleOp() {
        return bundleOp;
    }

    public void setBundleOp(String bundleOp) {
        this.bundleOp = bundleOp;
    }

    public List<Discount> getDiscounts() {
        return discounts;
    }

    public void setDiscounts(List<Discount> discounts) {
        this.discounts = discounts;
    }

    public String getComputationBase() {
        return computationBase;
    }

    public void setComputationBase(String computationBase) {
        this.computationBase = computationBase;
    }

    public String getReferencePeriod() {
        return referencePeriod;
    }

    public void setReferencePeriod(String referencePeriod) {
        this.referencePeriod = referencePeriod;
    }

    public Range getApplicableBaseRange() {
        return applicableBaseRange;
    }

    public void setApplicableBaseRange(Range applicableBaseRange) {
        this.applicableBaseRange = applicableBaseRange;
    }

    public Double getPercent() {
        return percent;
    }

    public void setPercent(Double percent) {
        this.percent = percent;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }
}