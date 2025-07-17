package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

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

	private RecurringPeriod billingPeriod;

	private List<String> agreements;

	public Plan() {
	}

	public Plan(String id, String name, String description, @Valid @NotNull TimePeriod validFor,
			String lifecycleStatus, @Valid @NotNull Price price, @Positive Integer contractDurationLength,
			RecurringPeriod contractDurationPeriodType, @Positive Integer renewalTermLength,
			RecurringPeriod renewalTermPeriodType, RecurringPeriod billingPeriod, List<String> agreements) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.validFor = validFor;
		this.lifecycleStatus = lifecycleStatus;
		this.price = price;
		this.contractDurationLength = contractDurationLength;
		this.contractDurationPeriodType = contractDurationPeriodType;
		this.renewalTermLength = renewalTermLength;
		this.renewalTermPeriodType = renewalTermPeriodType;
		this.billingPeriod = billingPeriod;
		this.agreements = agreements;
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

	public RecurringPeriod getBillingPeriod() {
		return billingPeriod;
	}

	public void setBillingPeriod(RecurringPeriod billingPeriod) {
		this.billingPeriod = billingPeriod;
	}

	public List<String> getAgreements() {
		return agreements;
	}

	public void setAgreements(List<String> agreements) {
		this.agreements = agreements;
	}
	
	@Override
	public String toString() {
		return "SubscriptionPlan [id=" + id + ", name=" + name + ", description=" + description + ", validFor="
				+ validFor + ", lifecycleStatus=" + lifecycleStatus + ", price=" + price
				+ ", contractDurationLength=" + contractDurationLength + ", contractDurationPeriodType="
				+ contractDurationPeriodType + ", renewalTermLength=" + renewalTermLength
				+ ", renewalTermPeriodType=" + renewalTermPeriodType + ", billingPeriod=" + billingPeriod
				+ ", agreements=" + agreements + "]";
	}

}
