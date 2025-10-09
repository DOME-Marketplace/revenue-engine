package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.eng.dome.tmforum.tmf620.v4.model.TimePeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class PlanItem {

	/**
	 * Descriptive field. Will appear in reports.
	 */
    @JsonProperty("name")
	private String name;

	/**
	 * A description for the benefit of the DOME operator only, not disclosed to subscribers within bills. 
	 * This might be useful to describe the rationale of the Plan Item, to provide references to external documentation.
	 */
	@JsonProperty("comment")
	private String comment;

	/**
	 * Bundle management
	 */
    @JsonProperty("isBundle")
    private Boolean isBundle;
    
    @JsonProperty("bundleOp")
    private BundleOperator bundleOp;

	@JsonProperty("forEachMetric")
	private String forEachMetric;

	/**
	 * The following property provide information about the due amount, either as absolute value or in perc.
	 */
    @JsonProperty("amount")
    @PositiveOrZero
    private Double amount;

    @JsonProperty("currency")
    private String currency;
    
	/**
	 * Items satisfying the following properties are completely skipped from the computation and reports.
	 * - ignore (Boolean)
	 * - ignorePeriod (String)
	 * - validFor (Range)
	 * - validForPeriod (String) 
	 */
	@JsonProperty("ignore")
	private String ignore;

	@JsonProperty("ignorePeriod")
	private ReferencePeriod ignorePeriod;

	@JsonProperty("validBetween")
	private TimePeriod validBetween;

	@JsonProperty("validPeriod")
	private ReferencePeriod validPeriod;

	/**
	 * The following properties are used to check if the computation is applicable. If not, the item
	 * is skipped from computation (and not included in the report? To be confirmed).
	 */
    @JsonProperty("applicableBase")
    private String applicableBase;

    @JsonProperty("applicableBaseRange")
    @Valid
    private Range applicableBaseRange;

	@JsonProperty("applicableBaseReferencePeriod")
	@Deprecated 
    private ReferencePeriod applicableBaseReferencePeriod;


//	@JsonProperty("applicableFrom")
//	@Deprecated
//	private OffsetDateTime applicableFrom;
	
	/**
	 * These properties drive the computation, in particular when the price/discount is expressed as a percentage.
	 */
	@JsonProperty("computationBase")
	private String computationBase;
	
	@JsonProperty("computationBaseReferencePeriod")
    private ReferencePeriod computationBaseReferencePeriod;

	@JsonProperty("percent")
    @PositiveOrZero
    @Max(100)
    private Double percent;

	/**
	 * this is to force the resulting amount in the given range
	 */
	@JsonProperty("resultingAmountRange")
	private Range resultingAmountRange;

	/**
	 * when true, the descendent items are discarded and the resulting item is like an atomic item.
	 * 
	 */
	@JsonProperty("collapse")
	private Boolean collapse;

	/**
	 * Whenever the resulting value of the computation is zero, this item (and all its descendants) is discarded
	 */
	@JsonProperty("skipIfZero")
	private Boolean skipIfZero;

	/**
	 * Items satisfying the following properties are included in reports, but their amount is zeroed.
	 * Either zero or compute fields are allowed in the same item. If more than one is specified, their
	 * union is considered.
	 * - zero (Boolean)
	 * - zeroPeriod (String)
	 * - zeroBetween (TimePeriod)
	 * - computePeriod (String)
	 * - computeBetween (TimePeriod)
	 */
	// TODO: implement the above

//	@JsonProperty("computationFrom")
//	private OffsetDateTime computationFrom;


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

	/**
	 * Propagated. Can be overridden.
	 * @return
	 */
    public String getComputationBase() {
		if(this.computationBase!=null)
			return this.computationBase;
		else if(this.getParentItem()!=null)
			return this.getParentItem().getComputationBase();
		return null;
	}

	public void setComputationBase(String computationBase) {
		this.computationBase = computationBase;
	}

    public Range getApplicableBaseRange() {
		/*
		if(this.applicableBaseRange!=null)
			return this.applicableBaseRange;
		else if(this.getParentItem()!=null)
			return this.getParentItem().getApplicableBaseRange();
		return null;
		*/
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

	@JsonIgnore
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

	/**
	 * Propagated. Can be overridden.
	 * @return
	 */
	public ReferencePeriod getComputationBaseReferencePeriod() {
		if(this.computationBaseReferencePeriod!=null)
			return this.computationBaseReferencePeriod;
		else if(this.getParentItem()!=null)
			return this.getParentItem().getComputationBaseReferencePeriod();
		return null;
		/*
		if(this.getParentItem()!=null && this.getParentItem().getComputationBaseReferencePeriod()!=null)
			return this.getParentItem().getComputationBaseReferencePeriod();
		else
			return this.computationBaseReferencePeriod;
		*/
	}

	public void setComputationBaseReferencePeriod(ReferencePeriod computationBaseReferencePeriod) {
		this.computationBaseReferencePeriod = computationBaseReferencePeriod;
	}

	/**
	 * @deprecated getValidBetween().getStartDateTime()
	 */
	public OffsetDateTime getApplicableFrom() {
		TimePeriod validBetween = this.getValidBetween();
		if(validBetween!=null)
			return validBetween.getStartDateTime();
		else
			return null;
	}

	public void setValidBetween(TimePeriod validBetween) {
		this.validBetween = validBetween;
	}

	/**
	 * Propagated. Inherited value hides the current (lower) setting.
	 * @return
	 */
	public TimePeriod getValidBetween() {
		if(this.getParentItem()!=null && this.getParentItem().getValidBetween()!=null)
			return this.getParentItem().getValidBetween();
		return this.validBetween;
	}

	/*
	public OffsetDateTime getComputationFrom() {
		if(this.getParentItem()!=null && this.getParentItem().getComputationFrom()!=null)
			return this.getParentItem().getComputationFrom();
		else
			return this.computationFrom;
	}

	public void setComputationFrom(OffsetDateTime computationFrom) {
		this.computationFrom = computationFrom;
	}
	*/

	/**
	 * Propagated. Inherited value hides the current (lower) setting.
	 * @return
	 */
	public ReferencePeriod getIgnorePeriod() {
		if(this.getParentItem()!=null && this.getParentItem().getIgnorePeriod()!=null)
			return this.getParentItem().getIgnorePeriod();
		else {
			return this.ignorePeriod;
		}
	}

	/**
	 * Propagated. Inherited value hides the current (lower) setting.
	 * @return
	 */
	public ReferencePeriod getValidPeriod() {
		if(this.getParentItem()!=null && this.getParentItem().getValidPeriod()!=null)
			return this.getParentItem().getValidPeriod();
		else {
			return this.validPeriod;
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
	@JsonIgnore
	public abstract List<PlanItem> getBundleItems();

	/**
	 * Return all child items (thus including prices AND discount)
	 * @return
	 */
	@JsonIgnore
	public abstract List<PlanItem> getChildItems();

	/**
	 * The closest ancestor reference price.
	 * @return
	 */
	@JsonIgnore
	public abstract Price getReferencePrice();


	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public Range getResultingAmountRange() {
		return resultingAmountRange;
	}

	public void setResultingAmountRange(Range resultingAmountRange) {
		this.resultingAmountRange = resultingAmountRange;
	}

	public Boolean getCollapse() {
		return Boolean.TRUE.equals(collapse);
	}

	public void setCollapse(Boolean collapse) {
		this.collapse = collapse;
	}

	public Boolean getSkipIfZero() {
		return Boolean.TRUE.equals(skipIfZero);
	}

	public void setSkipIfZero(Boolean skipIfZero) {
		this.skipIfZero = skipIfZero;
	}

	public void setValidPeriod(ReferencePeriod validPeriod) {
		this.validPeriod = validPeriod;
	}
	
}
