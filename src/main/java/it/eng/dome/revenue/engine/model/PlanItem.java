package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.List;

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

    @JsonProperty("ignore")
	private String ignore;

    @JsonProperty("forEachMetric")
	private String forEachMetric;

	// reference to the parent price, if any
	@JsonIgnore
	private PlanItem parentItem;
	
	public String getIgnore() {
			return this.ignore;
	}

	public void setIgnore(String ignore) {
		this.ignore = ignore;
	}

	public String getApplicableBase() {
		if(this.getParentItem()!=null && this.getParentItem().getApplicableBase()!=null)
			return this.getParentItem().getApplicableBase();
		else
			return this.applicableBase;
	}

	public void setApplicableBase(String applicableBase) {
		this.applicableBase = applicableBase;
	}

	public String getName() {
			return this.name;
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
		if(this.getParentItem()!=null && this.getParentItem().getComputationBase()!=null)
			return this.getParentItem().getComputationBase();
		else
			return this.computationBase;
	}

	public void setComputationBase(String computationBase) {
		this.computationBase = computationBase;
	}

    public Range getApplicableBaseRange() {
		if(this.getParentItem()!=null && this.getParentItem().getApplicableBaseRange()!=null)
			return this.getParentItem().getApplicableBaseRange();
		else
			return this.applicableBaseRange;
	}

	public void setApplicableBaseRange(Range applicableBaseRange) {
		this.applicableBaseRange = applicableBaseRange;
	}

	public String getCurrency() {
		if(this.getParentItem()!=null && this.getParentItem().getCurrency()!=null)
			return this.getParentItem().getCurrency();
		else
			return this.currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

    public void setParentItem(PlanItem parentItem) {
		this.parentItem = parentItem;
	}

	public PlanItem getParentItem() {
		return this.parentItem;
	}
	
	public Double getPercent() {
		return percent;
	}

	public PriceType getType() {
		return null;
	}

	public void setPercent(Double percent) {
		this.percent = percent;
	}

	public ReferencePeriod getApplicableBaseReferencePeriod() {
		if(this.getParentItem()!=null && this.getParentItem().getApplicableBaseReferencePeriod()!=null)
			return this.getParentItem().getApplicableBaseReferencePeriod();
		else
			return this.applicableBaseReferencePeriod;
	}

	public void setApplicableBaseReferencePeriod(ReferencePeriod applicableBaseReferencePeriod) {
		this.applicableBaseReferencePeriod = applicableBaseReferencePeriod;
	}

	public ReferencePeriod getComputationBaseReferencePeriod() {
		if(this.getParentItem()!=null && this.getParentItem().getComputationBaseReferencePeriod()!=null)
			return this.getParentItem().getComputationBaseReferencePeriod();
		else
			return this.computationBaseReferencePeriod;
	}

	public void setComputationBaseReferencePeriod(ReferencePeriod computationBaseReferencePeriod) {
		this.computationBaseReferencePeriod = computationBaseReferencePeriod;
	}

	public OffsetDateTime getApplicableFrom() {
		if(this.getParentItem()!=null && this.getParentItem().getApplicableFrom()!=null)
			return this.getParentItem().getApplicableFrom();
		else
			return this.applicableFrom;
	}

	public void setApplicableFrom(OffsetDateTime applicableFrom) {
		this.applicableFrom = applicableFrom;
	}

	public OffsetDateTime getComputationFrom() {
		if(this.getParentItem()!=null && this.getParentItem().getComputationFrom()!=null)
			return this.getParentItem().getComputationFrom();
		else
			return this.computationFrom;
	}

	public void setComputationFrom(OffsetDateTime computationFrom) {
		this.computationFrom = computationFrom;
	}

	public ReferencePeriod getIgnorePeriod() {
		if(this.getParentItem()!=null && this.getParentItem().getIgnorePeriod()!=null)
			return this.getParentItem().getIgnorePeriod();
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

	public String getForEachMetric() {
		return forEachMetric;
	}

	public void setForEachMetric(String forEachMetric) {
		this.forEachMetric = forEachMetric;
	}

	/**
	 * Return the bundled prices/discounts, depending on the specific type of the item
	 * @return
	 */
	public abstract List<PlanItem> getBundleItems();

	/**
	 * Return all child items (thus including prices AND discount)
	 * @return
	 */
	public abstract List<PlanItem> getChildItems();

	/**
	 * The closest ancestor price.
	 * @return
	 */
	public abstract Price getReferencePrice();

}
