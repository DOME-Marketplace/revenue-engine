package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Discount extends SubscriptionPlanItem{
	    
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
	
	@Override
	public String toString() {
		return "Discount [isBundle=" + getIsBundle() + ", bundleOp=" + getBundleOp() + ", discounts=" + discounts
				+ ", computationBase=" + this.getComputationBase() + ", referencePeriod=" + getReferencePeriod()
				+ ", applicableBaseRange=" + getApplicableBaseRange() + ", percent=" + getPercent() + ", amount=" + this.getAmount()
				+ "]";
	}

}