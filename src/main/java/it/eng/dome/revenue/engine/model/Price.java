package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Price {

    // informative
    private String name;
    private String description;

    // when the price is applied (pre, post, recurring, once, ...)
    private PriceType type;

    // if a bundle, include a list of prices
    private Boolean isBundle;
    private BundleOperator bundleOp;
    private List<Price> prices;

    // recurring info (ignore if type is not recurring)
    private Integer recurringChargePeriodLength;
    private RecurringPeriod recurringChargePeriodType;

    // On which basis the price is computed. The semantics of values is left to the implementation.
    private String computationBase;

    // Which period to consider for the computation
    private ReferencePeriod referencePeriod;

    // to which range of the computation base the price applies
    private Range applicableBaseRange;

    // the actual price (if not a bundle). If Bundled, ignore them.
    private Double amount;      // either a fixed amount
    private Double percent;     // ... or a percent of the computationBase
    private String currency;

    // boundary for the output computed price.
    private Range priceRange;

    
    public Price() {}
    
	public Price(String name, String description, PriceType type, Boolean isBundle, BundleOperator bundleOp,
			List<Price> prices, Integer recurringChargePeriodLength, RecurringPeriod recurringChargePeriodType,
			String computationBase, ReferencePeriod referencePeriod, Range applicableBaseRange, Double amount,
			Double percent, String currency, Range priceRange) {
		super();
		this.name = name;
		this.description = description;
		this.type = type;
		this.isBundle = isBundle;
		this.bundleOp = bundleOp;
		this.prices = prices;
		this.recurringChargePeriodLength = recurringChargePeriodLength;
		this.recurringChargePeriodType = recurringChargePeriodType;
		this.computationBase = computationBase;
		this.referencePeriod = referencePeriod;
		this.applicableBaseRange = applicableBaseRange;
		this.amount = amount;
		this.percent = percent;
		this.currency = currency;
		this.priceRange = priceRange;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public PriceType getType() {
		return type;
	}

	public void setType(PriceType type) {
		this.type = type;
	}

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

	public List<Price> getPrices() {
		return prices;
	}

	public void setPrices(List<Price> prices) {
		this.prices = prices;
	}

	public Integer getRecurringChargePeriodLength() {
		return recurringChargePeriodLength;
	}

	public void setRecurringChargePeriodLength(Integer recurringChargePeriodLength) {
		this.recurringChargePeriodLength = recurringChargePeriodLength;
	}

	public RecurringPeriod getRecurringChargePeriodType() {
		return recurringChargePeriodType;
	}

	public void setRecurringChargePeriodType(RecurringPeriod recurringChargePeriodType) {
		this.recurringChargePeriodType = recurringChargePeriodType;
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

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public Double getPercent() {
		return percent;
	}

	public void setPercent(Double percent) {
		this.percent = percent;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Range getPriceRange() {
		return priceRange;
	}

	public void setPriceRange(Range priceRange) {
		this.priceRange = priceRange;
	}

}