package it.eng.dome.revenue.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Price {
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("type")
    private String type; // Usato String invece di PriceType per maggiore flessibilità
    
    @JsonProperty("isBundle")
    private Boolean isBundle;
    
    @JsonProperty("bundleOp")
    private String bundleOp; // "CUMULATIVE", "ALTERNATIVE_HIGHER", ecc.
    
    // Per elementi bundle
    @JsonProperty("prices")
    private List<Price> prices;
    
    // Per elementi non bundle
    @PositiveOrZero
    @JsonProperty("amount")
    private Double amount;
    
    @JsonProperty("currency")
    private String currency;
    
    @PositiveOrZero
    @JsonProperty("percent")
    private Double percent;
    
    // Per prezzi ricorrenti
    @JsonProperty("recurringChargePeriodLength")
    private Integer recurringChargePeriodLength;
    
    @JsonProperty("recurringChargePeriodType")
    private String recurringChargePeriodType; // "DAY", "MONTH", "YEAR", ecc.
    
    @JsonProperty("discounts")
    private List<Discount> discounts;
    
    @JsonProperty("computationBase")
    private String computationBase;
    
    @JsonProperty("referencePeriod")
    private String referencePeriod;
    
    @JsonProperty("applicableBaseRange")
    private Range applicableBaseRange;

    public Price() {}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

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

	public String getRecurringChargePeriodType() {
		return recurringChargePeriodType;
	}

	public void setRecurringChargePeriodType(String recurringChargePeriodType) {
		this.recurringChargePeriodType = recurringChargePeriodType;
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
    
    

    
}