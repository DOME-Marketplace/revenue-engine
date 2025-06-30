package it.eng.dome.revenue.engine.model;

import java.util.List;

import jakarta.validation.Valid;


public class SubscriptionPlan {

    // informative
    private String name;
    private String description;

    // when the plan can be purchased
    private TimePeriod validFor;

    // the status of the plan (active, retired, launched, ...)
    private String lifecycleStatus;

    // a list of prices for this subscription plan
    @Valid
    private List<Price> price;

    // terms
    private Integer contractDurationLength;                 // es. 12
    private RecurringPeriod contractDurationPeriodType;     // es. MONTH

    private Integer renewalTermLength;                       // es. 1
    private RecurringPeriod renewalTermPeriodType;           // es. YEAR

    private RecurringPeriod billingPeriod;                   

    private List<String> agreements;                          

    

    public SubscriptionPlan() {}



    // getter e setter

    public SubscriptionPlan(String name, String description, TimePeriod validFor, String lifecycleStatus,
			List<Price> price, Integer contractDurationLength, RecurringPeriod contractDurationPeriodType,
			Integer renewalTermLength, RecurringPeriod renewalTermPeriodType, RecurringPeriod billingPeriod,
			List<String> agreements) {
		super();
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

    public List<Price> getPrice() {
        return price;
    }

    public void setPrice(List<Price> price) {
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


}
