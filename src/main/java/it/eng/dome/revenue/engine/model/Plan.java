package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import it.eng.dome.revenue.engine.utils.IdUtils;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Plan {

	private String id;
	private String name;
	private String description;

	// when the plan can be purchased
	@Valid
	@NotNull
	private TimePeriod validFor;

	// the status of the plan (active, retired, launched, ...)
	private String lifecycleStatus;
	
	private String offeringId;
	
	private String offeringPriceId;

	// a list of prices for this subscription plan
	@Valid
	@NotNull
	private Price price;

	// terms
	@Positive
	private Integer contractDurationLength; // es. 12
	private RecurringPeriod contractDurationPeriodType; // es. MONTH

	@Positive
	private Integer renewalTermLength; // es. 1
	private RecurringPeriod renewalTermPeriodType; // es. YEAR

	private BillCycleSpecification billCycleSpecification;

//	private Integer billingPeriodLength; // es. 1
//	private RecurringPeriod billingPeriodType;
//	private String billingPeriodEnd;

	private List<String> agreements;

	public Plan() {
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
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

	public TimePeriod getValidFor() {
		return validFor;
	}

	public void setValidFor(TimePeriod validFor) {
		this.validFor = validFor;
	}

	public String getLifecycleStatus() {
		return lifecycleStatus;
	}

	public void setLifecycleStatus(String lifecycleStatus) {
		this.lifecycleStatus = lifecycleStatus;
	}
	
	public String getOfferingId() {
		return offeringId;
	}

	public void setOfferingId(String offeringId) {
		this.offeringId = offeringId;
	}

	public String getOfferingPriceId() {
		return offeringPriceId;
	}

	public void setOfferingPriceId(String offeringPriceId) {
		this.offeringPriceId = offeringPriceId;
	}

	public Price getPrice() {
		return price;
	}

	public void setPrice(Price price) {
		this.price = price;
	}

	public Integer getContractDurationLength() {
		return contractDurationLength;
	}

	public void setContractDurationLength(Integer contractDurationLength) {
		this.contractDurationLength = contractDurationLength;
	}

	public RecurringPeriod getContractDurationPeriodType() {
		return contractDurationPeriodType;
	}

	public void setContractDurationPeriodType(RecurringPeriod contractDurationPeriodType) {
		this.contractDurationPeriodType = contractDurationPeriodType;
	}

	public Integer getRenewalTermLength() {
		return renewalTermLength;
	}

	public void setRenewalTermLength(Integer renewalTermLength) {
		this.renewalTermLength = renewalTermLength;
	}

	public RecurringPeriod getRenewalTermPeriodType() {
		return renewalTermPeriodType;
	}

	public void setRenewalTermPeriodType(RecurringPeriod renewalTermPeriodType) {
		this.renewalTermPeriodType = renewalTermPeriodType;
	}

	public List<String> getAgreements() {
		return agreements;
	}

	public void setAgreements(List<String> agreements) {
		this.agreements = agreements;
	}

	public Plan buildRef() {
		Plan planRef = new Plan();
		planRef.setId(this.getId());
		planRef.setName(this.getName());
		return planRef;
	}

	public BillCycleSpecification getBillCycleSpecification() {
		return this.billCycleSpecification;
	}

	public void setBillCycleSpecification(BillCycleSpecification billCycle) {
		this.billCycleSpecification = billCycle;
	}

	public String generateId(String offeringId, String priceId) {
		return IdUtils.pack("plan", offeringId, priceId);
	}

	@Override
	public String toString() {
		return "SubscriptionPlan [id=" + id + ", name=" + name + ", description=" + description + ", validFor="
				+ validFor + ", lifecycleStatus=" + lifecycleStatus + ", price=" + price
				+ ", contractDurationLength=" + contractDurationLength + ", contractDurationPeriodType="
				+ contractDurationPeriodType + ", renewalTermLength=" + renewalTermLength
				+ ", renewalTermPeriodType=" + renewalTermPeriodType + ", billingPeriodLength=" + this.getBillCycleSpecification().getBillingPeriodLength()
				+ ", agreements=" + agreements + "]";
	}

}
