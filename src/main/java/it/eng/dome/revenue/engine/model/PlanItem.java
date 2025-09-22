package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public abstract class PlanItem {

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
    
    @JsonProperty("applicableBase")
    private String applicableBase;

    @JsonProperty("applicableBaseRange")
    @Valid
    private Range applicableBaseRange;

	@JsonProperty("applicableBaseReferencePeriod")
	@Deprecated 
    private ReferencePeriod applicableBaseReferencePeriod;

	@JsonProperty("ignorePeriod")
	private ReferencePeriod ignorePeriod;

	@JsonProperty("applicableFrom")
	private OffsetDateTime applicableFrom;
	
	@JsonProperty("computationBase")
	private String computationBase;
	
	@JsonProperty("computationBaseReferencePeriod")
	@Deprecated 
    private ReferencePeriod computationBaseReferencePeriod;

	@JsonProperty("computationFrom")
	private OffsetDateTime computationFrom;

	// reference to the parent price, if any
	@JsonIgnore
	private Price parentPrice;
	
	public String getApplicableBase() {
		if(this.getParentPrice()!=null && this.getParentPrice().getApplicableBase()!=null)
			return this.getParentPrice().getApplicableBase();
		else
			return this.applicableBase;
	}

	public void setApplicableBase(String applicableBase) {
		this.applicableBase = applicableBase;
	}

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
		if(this.getParentPrice()!=null && this.getParentPrice().getComputationBase()!=null)
			return this.getParentPrice().getComputationBase();
		else
			return this.computationBase;
	}

	public void setComputationBase(String computationBase) {
		this.computationBase = computationBase;
	}

    public Range getApplicableBaseRange() {
		if(this.getParentPrice()!=null && this.getParentPrice().getApplicableBaseRange()!=null)
			return this.getParentPrice().getApplicableBaseRange();
		else
			return this.applicableBaseRange;
	}

	public void setApplicableBaseRange(Range applicableBaseRange) {
		this.applicableBaseRange = applicableBaseRange;
	}

	public String getCurrency() {
		if(this.getParentPrice()!=null && this.getParentPrice().getCurrency()!=null)
			return this.getParentPrice().getCurrency();
		else
			return this.currency;
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
		if(this.getParentPrice()!=null && this.getParentPrice().getApplicableBaseReferencePeriod()!=null)
			return this.getParentPrice().getApplicableBaseReferencePeriod();
		else
			return this.applicableBaseReferencePeriod;
	}

	public void setApplicableBaseReferencePeriod(ReferencePeriod applicableBaseReferencePeriod) {
		this.applicableBaseReferencePeriod = applicableBaseReferencePeriod;
	}

	public ReferencePeriod getComputationBaseReferencePeriod() {
		if(this.getParentPrice()!=null && this.getParentPrice().getComputationBaseReferencePeriod()!=null)
			return this.getParentPrice().getComputationBaseReferencePeriod();
		else
			return this.computationBaseReferencePeriod;
	}

	public void setComputationBaseReferencePeriod(ReferencePeriod computationBaseReferencePeriod) {
		this.computationBaseReferencePeriod = computationBaseReferencePeriod;
	}

	public OffsetDateTime getApplicableFrom() {
		if(this.getParentPrice()!=null && this.getParentPrice().getApplicableFrom()!=null)
			return this.getParentPrice().getApplicableFrom();
		else
			return this.applicableFrom;
	}

	public void setApplicableFrom(OffsetDateTime applicableFrom) {
		this.applicableFrom = applicableFrom;
	}

	public OffsetDateTime getComputationFrom() {
		if(this.getParentPrice()!=null && this.getParentPrice().getComputationFrom()!=null)
			return this.getParentPrice().getComputationFrom();
		else
			return this.computationFrom;
	}

	public void setComputationFrom(OffsetDateTime computationFrom) {
		this.computationFrom = computationFrom;
	}

	public ReferencePeriod getIgnorePeriod() {
		if(this.getParentPrice()!=null && this.getParentPrice().getIgnorePeriod()!=null)
			return this.getParentPrice().getIgnorePeriod();
		else {
				return this.ignorePeriod;
		}
	}

	public void setIgnorePeriod(ReferencePeriod ignorePeriod) {
		this.ignorePeriod = ignorePeriod;
	}

	@JsonIgnore
	public boolean isConditional() {
		if(this.getApplicableBase()!=null || this.getApplicableBaseRange()!=null || this.getApplicableBaseReferencePeriod()!=null || this.getApplicableFrom()!=null)
			return true;
		if(this.getPercent()!=null)
			return true;
		return false;
	}

	public abstract boolean isVariable();

}
