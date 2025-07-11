package it.eng.dome.revenue.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public class SubscriptionPlanItem {

    @JsonProperty("name")
	private String name;

    @JsonProperty("isBundle")
    private Boolean isBundle;
    
    @JsonProperty("bundleOp")
    private BundleOperator bundleOp;

    // For not bundle elements
    @JsonProperty("amount")
    @PositiveOrZero
    private Double amount;

    @JsonProperty("percent")
    @PositiveOrZero
    @Max(100)
    private Double percent;

    @JsonProperty("currency")
    private String currency;
    

    @JsonProperty("computationBase")
    private String computationBase;

    @JsonProperty("applicableBaseRange")
    @Valid
    private Range applicableBaseRange;

	@JsonProperty("applicableBaseReferencePeriod")
	@Deprecated 
    private ReferencePeriod applicableBaseReferencePeriod;

	@JsonProperty("computationBaseReferencePeriod")
	@Deprecated 
    private ReferencePeriod computationBaseReferencePeriod;

	// reference to the parent price, if any
	@JsonIgnore
	private Price parentPrice;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Boolean getIsBundle() {
		return (this.isBundle!=null && this.isBundle==true);
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

    	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

    public String getComputationBase() {
		return computationBase;
	}

	public void setComputationBase(String computationBase) {
		this.computationBase = computationBase;
	}

    public Range getApplicableBaseRange() {
		return applicableBaseRange;
	}

	public void setApplicableBaseRange(Range applicableBaseRange) {
		this.applicableBaseRange = applicableBaseRange;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

    public void setParentPrice(Price parentPrice) {
		this.parentPrice = parentPrice;
	}

	public Price getParentPrice() {
		return parentPrice;
	}
	
	public Double getPercent() {
		return percent;
	}

	public void setPercent(Double percent) {
		this.percent = percent;
	}

	public ReferencePeriod getApplicableBaseReferencePeriod() {
		return applicableBaseReferencePeriod;
	}

	public void setApplicableBaseReferencePeriod(ReferencePeriod applicableBaseReferencePeriod) {
		this.applicableBaseReferencePeriod = applicableBaseReferencePeriod;
	}

	public ReferencePeriod getComputationBaseReferencePeriod() {
		return computationBaseReferencePeriod;
	}

	public void setComputationBaseReferencePeriod(ReferencePeriod computationBaseReferencePeriod) {
		this.computationBaseReferencePeriod = computationBaseReferencePeriod;
	}


}
