package it.eng.dome.revenue.engine.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Discount extends PlanItem{
	    
    @JsonProperty("discounts")
    @Valid
    private List<Discount> discounts;
                
	public Discount() {}

	public List<Discount> getDiscounts() {
		return discounts;
	}

	public void setDiscounts(List<Discount> discounts) {
		this.discounts = discounts;
	}
	
	@JsonIgnore
	public boolean isVariable() {
		if(this.discounts!=null) {
			for(Discount d: this.discounts) {
				if(d.isVariable())
					return true;
			}
		}
		if(this.isConditional())
			return true;
		return false;
	}

	@JsonIgnore
	public List<PlanItem> getChildItems() {
		return this.getBundleItems();
	}

	@JsonIgnore
	public List<PlanItem> getBundleItems() {
	    List<PlanItem> out = new ArrayList<>();
	    if (this.getDiscounts() != null) {
	        out.addAll(this.getDiscounts());
	    }
	    return out;
	}


	@JsonIgnore
	public Price getReferencePrice() {
		PlanItem parent = this.getParentItem();
		if(parent!=null) {
			if(parent instanceof Price)
				return (Price)parent;
			else
				return parent.getReferencePrice();
		} else {
			return null;
		}
	}

	@Override
	public String toString() {
		return "Discount [isBundle=" + getIsBundle() + ", bundleOp=" + getBundleOp() + ", discounts=" + discounts
				+ ", computationBase=" + this.getComputationBase() + ", ABreferencePeriod=" + getApplicableBaseReferencePeriod()
				+ ", CBreferencePeriod=" + getComputationBaseReferencePeriod()
				+ ", applicableBaseRange=" + getApplicableBaseRange() + ", percent=" + getPercent() + ", amount=" + this.getAmount()
				+ "]";
	}

}