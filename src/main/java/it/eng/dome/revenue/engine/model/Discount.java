package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Discount {
	
    @JsonProperty("isBundle")
    private Boolean isBundle;
    
    @JsonProperty("bundleOp")
    private BundleOperator bundleOp;
    
    @JsonProperty("discounts")
    @Valid
    private List<Discount> discounts;
    
    @JsonProperty("computationBase")
    private String computationBase;
    
    @JsonProperty("referencePeriod")
    private ReferencePeriod referencePeriod;
    
    @JsonProperty("applicableBaseRange")
    @Valid
    private Range applicableBaseRange;
      
    @JsonProperty("percent")
    @PositiveOrZero
    @Max(100)
    private Double percent;
    
    @JsonProperty("amount")
    @PositiveOrZero
    private Double amount;

	// reference to the parent price, if any
	@JsonIgnore
	private Price parentPrice;

	public Discount() {}

	public Boolean getIsBundle() {
		return isBundle;
	}

	public void setIsBundle(Boolean isBundle) {
		this.isBundle = isBundle;
	}

	public BundleOperator getBundleOp() {
		return bundleOp;
	}

	public void setBundleOp(BundleOperator bundleOp) {
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

	public ReferencePeriod getReferencePeriod() {
		return referencePeriod;
	}

	public void setReferencePeriod(ReferencePeriod referencePeriod) {
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

   	public void setParentPrice(Price parentPrice) {
		this.parentPrice = parentPrice;
	}

	public Price getParentPrice() {
		return parentPrice;
	}
	
	@Override
	public String toString() {
		return "Discount [isBundle=" + isBundle + ", bundleOp=" + bundleOp + ", discounts=" + discounts
				+ ", computationBase=" + computationBase + ", referencePeriod=" + referencePeriod
				+ ", applicableBaseRange=" + applicableBaseRange + ", percent=" + percent + ", amount=" + amount
				+ "]";
	}

}