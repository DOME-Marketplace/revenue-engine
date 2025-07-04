package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

	// reference to the parent price, if any
    @JsonIgnore
    @PositiveOrZero
	Price parentPrice;
    
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
		if (prices != null) {
			for (Price price : prices) {
				price.setParentPrice(this);
			}
		}	
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

	/* Return the inherited length, if any. Otherwise the local value. */
	public Integer getRecurringChargePeriodLength() {
		Integer inheritedLength;
		if(this.getParentPrice() != null) {
			inheritedLength = this.getParentPrice().getRecurringChargePeriodLength();
		} else {
			inheritedLength = null;
		}
		if(inheritedLength != null) {
			return inheritedLength;
		} else {}
			return this.recurringChargePeriodLength;
	}

	public void setRecurringChargePeriodLength(Integer recurringChargePeriodLength) {
		this.recurringChargePeriodLength = recurringChargePeriodLength;
	}

	/* Return the inherited period type, if any. Otherwise the local value. */
	public RecurringPeriod getRecurringChargePeriodType() {
		RecurringPeriod inheritedPeriod;
		if(this.getParentPrice() != null) {
			inheritedPeriod = this.getParentPrice().getRecurringChargePeriodType();
		} else {
			inheritedPeriod = null;
		}
		if(inheritedPeriod != null) {
			return inheritedPeriod;
		} else {}
			return this.recurringChargePeriodType;
	}

	public void setRecurringChargePeriodType(RecurringPeriod recurringChargePeriodType) {
		this.recurringChargePeriodType = recurringChargePeriodType;
	}

	public Discount getDiscount() {
		return discount;
	}

	public void setDiscount(Discount discount) {
		this.discount = discount;
		if (discount != null) {
			discount.setParentPrice(this);
		}
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

    public void setParentPrice(Price parentPrice) {
		this.parentPrice = parentPrice;
	}

	public Price getParentPrice() {
		return parentPrice;
	}

}