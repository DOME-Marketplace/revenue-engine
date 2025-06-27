package it.eng.dome.revenue.engine.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class Discount {

    // informative
    private String name;
    private String description;

    // if a bundle, include a list of prices
    private Boolean isBundle;
    private BundleOperator bundleOp;
    private List<Discount> discounts;

    // On which basis the discount is computed. The semantics of values is left to the implementation.
    private String computationBase;

    // Which period to consider for the computation of the discount
    private ReferencePeriod referencePeriod;

    // to which range the discount applies
    private Range applicableBaseRange;

    // the actual discount (if not a bundle). If Bundled, ignore them.
    private Double amount;      // either a fixed amount
    private Double percent;     // ... or a percent of the computationBase
    
    
       
    
	public Discount() {
	}

	public Discount(String name, String description, Boolean isBundle, BundleOperator bundleOp,
			List<Discount> discounts, String computationBase, ReferencePeriod referencePeriod,
			Range applicableBaseRange, Double amount, Double percent) {
		super();
		this.name = name;
		this.description = description;
		this.isBundle = isBundle;
		this.bundleOp = bundleOp;
		this.discounts = discounts;
		this.computationBase = computationBase;
		this.referencePeriod = referencePeriod;
		this.applicableBaseRange = applicableBaseRange;
		this.amount = amount;
		this.percent = percent;
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

}