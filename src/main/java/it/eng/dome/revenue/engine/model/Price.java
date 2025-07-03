package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Price {
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("type")
    private PriceType type;
    
    @JsonProperty("isBundle")
    private Boolean isBundle;
    
    @JsonProperty("bundleOp")
    private BundleOperator bundleOp; // "CUMULATIVE", "ALTERNATIVE_HIGHER", ecc.
    
    // For bundle elements
    @JsonProperty("prices")
    @Valid
    private List<Price> prices;
    
    // For not bundle elements
    @JsonProperty("amount")
    @PositiveOrZero
    private Double amount;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("percent")
    @PositiveOrZero
    @Max(100)
    private Double percent;
    
    // For recurring price
    @JsonProperty("recurringChargePeriodLength")
    @Positive
    private Integer recurringChargePeriodLength;
    
    @JsonProperty("recurringChargePeriodType")
    private RecurringPeriod recurringChargePeriodType; // "DAY", "MONTH", "YEAR", ecc.
    
    @JsonProperty("discount")
    @Valid
    private Discount discount;
    
    @JsonProperty("computationBase")
    private String computationBase;
    
    @JsonProperty("referencePeriod")
    private ReferencePeriod referencePeriod;
    
    @JsonProperty("applicableBaseRange")
    @Valid
    private Range applicableBaseRange;

    public Price() {}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public Double getAmount() {
		return amount;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Double getPercent() {
		return percent;
	}

	public void setPercent(Double percent) {
		this.percent = percent;
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

	public Discount getDiscount() {
		return discount;
	}

	public void setDiscount(Discount discount) {
		this.discount = discount;
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
}