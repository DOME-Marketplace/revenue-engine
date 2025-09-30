package it.eng.dome.revenue.engine.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Price extends PlanItem {
    
    @JsonProperty("type")
    private PriceType type;
        
    // For bundle elements
    @JsonProperty("prices")
    @Valid
    private List<Price> prices;
        
    // For recurring price
    @JsonProperty("recurringChargePeriodLength")
    @Positive
    private Integer recurringChargePeriodLength;
    
    @JsonProperty("recurringChargePeriodType")
    private RecurringPeriod recurringChargePeriodType; // "DAY", "MONTH", "YEAR", ecc.
    
    @JsonProperty("discount")
    @Valid
    private Discount discount;

    public Price() {}

	/* Return the inherited type, if any. Otherwise the local value. */
	public PriceType getType() {
		PriceType inheritedType = null;
		if(this.getParentPrice() != null) {
			inheritedType = this.getParentPrice().getType();
		}
		if(inheritedType != null) {
			return inheritedType;
		} else {
			return this.type;
		}
	}


	public void setType(PriceType type) {
		this.type = type;
	}

	public List<Price> getPrices() {
		return prices;
	}

	public void setPrices(List<Price> prices) {
		this.prices = prices;
		if (prices != null) {
			for (Price price : prices) {
				price.setParentItem(this);
			}
		}	
	}

	public Price getParentPrice() {
		PlanItem parent = this.getParentItem();
		if(parent!=null && parent instanceof Price)
			return (Price)parent;
		return null;
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
			discount.setParentItem(this);
		}
	}

	public boolean isVariable() {
		if(this.prices!=null)
			for(Price p: this.prices) {
				if(p.isVariable())
					return true;
			}
		if(this.discount!=null && this.discount.isVariable())
			return true;
		if(this.isConditional())
			return true;
		return false;
	}

	public List<PlanItem> getBundleItems() {
		List<PlanItem> out = new ArrayList<>();
		out.addAll(this.getPrices());
		return out;
	}

	public List<PlanItem> getChildItems() {
		List<PlanItem> out = this.getBundleItems();
		out.add(this.getDiscount());
		return out;
	}

	public Price getReferencePrice() {
		return this;
	}

	@Override
	public String toString() {
		return "Price [name=" + this.getName() + ", type=" + type + ", isBundle=" + this.getIsBundle() + ", bundleOp=" + this.getBundleOp()
				+ ", prices=" + prices + ", amount=" + this.getAmount() + ", currency=" + getCurrency() + ", percent=" + getPercent()
				+ ", recurringChargePeriodLength=" + recurringChargePeriodLength
				+ ", recurringChargePeriodType=" + recurringChargePeriodType + ", discount=" + discount
				+ ", computationBase=" + getComputationBase() + ", ABreferencePeriod=" + getApplicableBaseReferencePeriod()
				+ ", CBreferencePeriod=" + getComputationBaseReferencePeriod()
				+ ", applicableBaseRange=" + getApplicableBaseRange() + "]";
	}
}