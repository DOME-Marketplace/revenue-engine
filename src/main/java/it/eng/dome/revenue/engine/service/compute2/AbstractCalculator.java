package it.eng.dome.revenue.engine.service.compute2;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.Discount;
import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.Range;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.service.MetricsRetriever;
import it.eng.dome.revenue.engine.service.TmfDataRetriever;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public abstract class AbstractCalculator implements Calculator {

    private static final Logger logger = LoggerFactory.getLogger(AbstractCalculator.class);

	protected PlanItem item;

	MetricsRetriever metricsRetriever;

	TmfDataRetriever tmfDataRetriever;

    private Subscription subscription;

	private Map<String, String> calculatorContext;

	public AbstractCalculator(Subscription subscription, PlanItem item) {
        this.subscription = subscription;
        this.item = item;
		this.calculatorContext = new HashMap<>();
    }

	public void setMetricsRetriever(MetricsRetriever mr) {
		this.metricsRetriever = mr;
	}

	public void setTmfDataRetriever(TmfDataRetriever tdr) {
		this.tmfDataRetriever = tdr;
	}

	public final RevenueItem compute(TimePeriod timePeriod, Map<String, Double> computeContext) {

		// check if it's to skip or not
		logger.debug("checking preconditions...");
		if(!this.checkPreconditions(timePeriod)) {
			return null;
		}
		logger.debug("preconditions OK");

		logger.debug("checking computability...");
		if(!this.checkComputability(timePeriod)) {
			return null;
		}
		logger.debug("computability OK");

		logger.debug("checking applicability...");
		if(!this.checkApplicability(timePeriod)) {
			return null;
		}
		logger.debug("applicability OK");

		// check if it has to be zeroed (although we keep the structure)
		boolean zeroIt = this.checkZeroIt(timePeriod);
		logger.debug("zeroIt? {}", zeroIt);

		// do build the structure and value
		logger.debug("doComputing...");
		RevenueItem outRevenueItem = this.doCompute(timePeriod, computeContext);
		logger.debug("outRevenueItem: {}", outRevenueItem);

		// if nothing is returned, exit
		if(outRevenueItem==null)
			return null;

		if(this.item instanceof Price) {
			// TODO: if this works here, remove the setting done in atomic calculators
			outRevenueItem.setChargeTime(new SubscriptionTimeHelper(this.getSubscription()).getChargeTime(timePeriod, this.item.getReferencePrice()));
		}

		// zero the item, if needed
		if(zeroIt) {
			logger.debug("zero-ing the item");
			outRevenueItem.zeroAmountsRecursively();
		}

		// constrain the resulting value, if needed
		Range r = this.item.getResultingAmountRange();
		if (r != null && this.item instanceof Discount) {
			// for discounts, reverse the prices, since values in items are negative for discounts
		    r = new Range(-r.getMax(), -r.getMin());
		}

		if(r!=null) {
			logger.debug("enforcing resulting amount range...");
			if(r.getMin()!=null) {
				logger.debug("constraining overallValue to be >= than {}", r.getMin());
				if(outRevenueItem.getOverallValue()<r.getMin()) {
					Double diff = r.getMin()-outRevenueItem.getOverallValue();
					Double newVal = outRevenueItem.getValue()+diff;
					outRevenueItem.setValue(newVal);
				}
			}
			if(r.getMax()!=null) {
				logger.debug("constraining overallValue to be <= than {}", r.getMax());
				if(outRevenueItem.getOverallValue()>r.getMax()) {
					Double diff = outRevenueItem.getOverallValue()-r.getMax();
					Double newVal = outRevenueItem.getValue()-diff;
					outRevenueItem.setValue(newVal);
				}
			}
		}

		// if requested, forget the revenue item, if the result is zero
		if(this.item.getSkipIfZero() && outRevenueItem.getOverallValue()==0) {
			logger.debug("overall value is zero and 'skipIfZero' is true... returning null");
			return null;
		}

		// variable values for future items lead to estimaed revenueItems
		// FIXME: the second check below is to move ahead with development, but shouldn't be there
		if (outRevenueItem!=null && outRevenueItem.getChargeTime()!=null) {
			logger.debug("setting the estimated flag...");
			if(this.item.isVariable() && outRevenueItem.getChargeTime().isAfter(OffsetDateTime.now())) {
				outRevenueItem.setEstimated(true);
			} else {
				outRevenueItem.setEstimated(false);
			}
		}

		// as a final step, if needed, collapse the revenueItem
		if(outRevenueItem!=null && this.item.getCollapse()) {
			// an item can be collapsed only if it has a chargeTime
			if(outRevenueItem.getChargeTime()!=null) {
				logger.debug("collapsing...");
				// set the value to be the same as getOverallValue() 
				outRevenueItem.setValue(outRevenueItem.getOverallValue());
				// remove all child items
				outRevenueItem.setItems(new ArrayList<>());
			}
			else {
				logger.warn("can't collapse {} because the item has no charge time", outRevenueItem.getName());
			}
		}

		logger.debug("returing {} for addition", outRevenueItem);

		return outRevenueItem;
	}

	/**
	 *  This is the method to be implemented bu subclasses.
	 */
    protected abstract RevenueItem doCompute(TimePeriod timePeriod, Map<String, Double> computeContext);

    /**
     * Make sure general conditions are true in order to proceed.
     * @param timePeriod
     * @return
     */
    private boolean checkPreconditions(TimePeriod timePeriod) {

		// check that the charge time period for the price corresponds with the one as parameter (only if not a bundle)
		// Q: why not applicable to bundles? if the bundle sets the periodicity and it doesn't match the timePeriod, skip it entirely
		// FIXME: check the true below (i.e. removing the condition) works. If so, remove it definitely
		/*
		if(true || !this.item.getIsBundle()) {
			TimePeriod tp = this.getChargeTimePeriod(timePeriod.getStartDateTime().plusSeconds(1));
			logger.debug("price/discount charge period is {}", tp);
			if (tp == null 
					|| tp.getStartDateTime()==null 
					|| tp.getEndDateTime()==null
					|| !tp.getStartDateTime().equals(timePeriod.getStartDateTime())
					|| !tp.getEndDateTime().equals(timePeriod.getEndDateTime())) {
				logger.debug("This item '{}' period {} does not match the statements period {}. Skipping.", this.item.getName(), tp, timePeriod);
				return false;
			}
		}
		*/

		TimePeriod tp = this.getChargeTimePeriod(timePeriod.getStartDateTime().plusSeconds(1));
		logger.debug("price/discount charge period is {}", tp);
		if(tp!=null && tp.getStartDateTime()!=null && tp.getEndDateTime()!=null) {
			if(!tp.getStartDateTime().equals(timePeriod.getStartDateTime()) || !tp.getEndDateTime().equals(timePeriod.getEndDateTime())) {
				logger.debug("This item '{}' period {} does not match the statements period {}. Skipping.", this.item.getName(), tp, timePeriod);
				return false;
			}
		} else {
			if(!this.item.getIsBundle()) {
				logger.debug("not a bundle, but missing tp information. Skipping.");
				return false;
			}
		}
		logger.debug("This item '{}' period matches the statements period {}.", this.item.getName(), timePeriod);

		// check the item is not to ignore
	    if ("true".equalsIgnoreCase(this.item.getIgnore())) {
	        logger.info("Ignoring price/discount {} based on ignore flag {}", this.item.getName(), this.item.getIgnore());
			return false;
	    }
		logger.info("The price/discount {} is not to be ignored", this.item.getName());


		// TODO: replace the following with a full support for "validFor"
		// check if the price is applicable in the given time period (using the applicableFrom attribute)
		// FIXME: here we only check that if start isBefore, the the whole period is not considered.
		// However, if the end is after, then the second half of the period should be considered.
		// In this case, a new TimePeriod should be considered, including only the second half.
		// Q: and what if the ignorePeriod is entirely contained (and smaller) in the charge period?
		// Need to split in two? Resulting in two items? Not working for flat prices... maybe it's price-dependent behaviour.
		// Needs more branistorming.
		if (this.item.getApplicableFrom() != null && timePeriod.getStartDateTime().isBefore(this.item.getApplicableFrom())) {
			logger.debug("Price {} not applicable for time period {} (applicable from {})", this.item.getName(), timePeriod,
					this.item.getApplicableFrom());
			return false;
		}
		logger.debug("[FIXME] Price {} is applicable for time period {} (applicable from {})", this.item.getName(), timePeriod, this.item.getApplicableFrom());

		// now also check the 'ignorePeriod' property. Resolve it and check if the period is affected.
		// FIXME: same considerations as above
		SubscriptionTimeHelper sth = new SubscriptionTimeHelper(this.getSubscription());
		if(this.item.getIgnorePeriod()!=null) {
		    TimePeriod ignorePeriod = sth.getCustomPeriod(null, (Price)this.item, this.item.getIgnorePeriod().getValue());
			if(ignorePeriod!=null) {
				logger.debug("For this price/discount, ignoring the period {} - {}", ignorePeriod.getStartDateTime(), ignorePeriod.getEndDateTime());
				if(timePeriod.getStartDateTime().isBefore(ignorePeriod.getEndDateTime())) {
					logger.debug("Ignoring the price/discount entirely as it starts within the ignorePeriod");
					return false;
				}
			}
			logger.debug("The price/discount starts outside the ignorePeriod {}", this.item.getIgnorePeriod());
		}

		// now also check the 'validPeriod' property. Resolve it and check if the period is affected.
		// FIXME: same considerations as above
		if(this.item.getValidPeriod()!=null) {
		    TimePeriod validPeriod = sth.getCustomPeriod(null, this.item.getReferencePrice(), this.item.getValidPeriod().getValue());
			if(validPeriod!=null) {
				logger.debug("For this price/discount, only considering the period {} - {}", validPeriod.getStartDateTime(), validPeriod.getEndDateTime());
				if(!timePeriod.getStartDateTime().isBefore(validPeriod.getEndDateTime())) {
					logger.debug("Ignoring the price/discount entirely as it starts after the validPeriod");
					return false;
				}
			}
			logger.debug("The price/discount starts before the validPeriod {}", this.item.getValidPeriod());
		}

        return true;
    }

	private boolean checkZeroIt(TimePeriod timePeriod) {

		// TODO: support for zero (Boolean)

		// TODO: support for zeroPeriod (String)

		// TODO: support for zeroBetween (TimePeriod)

		// TODO: support for computePeriod (String)

		// TODO: support for computeBetween (TimePeriod)

		return false;
	}


	/**
	 * Make sure that properties for applicability are set correctly and that conditions are satisfied
	 * @param tp
	 * @return
	 */
    private boolean checkApplicability(TimePeriod timePeriod) {

		if(this.item.getIsBundle())
			return true;

		String subscriberId = this.getSubscription().getSubscriberId();

		Double applicableValue = this.getApplicableValue(subscriberId, timePeriod);

		// value & condition => check
		// value & no condition => OK, with warning
		// no value & condition => KO, with error
		// no value & no condition => OK

		if(applicableValue!=null) {
			if (this.item.getApplicableBaseRange()!=null) {
				return this.item.getApplicableBaseRange().inRange(applicableValue);
			}
			else {
				logger.warn("The item {} specifies an applicability metric, but it sets no range for it");
				return true;
			}
		}
		else {
			if (this.item.getApplicableBaseRange()!=null) {
				logger.error("The item {} specifies an applicability range, but no metric is defined");
				return false;
			}
			else {
				return true;
			}
		}

	}

    /**
     * Make sure that properties for computation are correctly set and are available
     * @param tp
     * @return
     */
    private boolean checkComputability(TimePeriod timePeriod) {

		if(this.item.getIsBundle())
			return true;

		// for atomic items, make sure an amount or a percent are set
		if (this.item.getPercent() == null && this.item.getAmount() == null) {
			logger.warn("Neither percent nor amount defined for computation!");
			return false;
		}

		// make sure a computationBase is set (TODISCUSS: is this true? maybe a fixed amount do)
		if (this.item.getPercent()!=null && (this.item.getComputationBase() == null || this.item.getComputationBase().isEmpty())) {
			logger.warn("A percent is set, but no computation base defined!");
			return false;
		}

		// also a reference period for the computation base is needed
		if (this.item.getPercent()!=null && !"parent-price".equals(this.item.getComputationBase()) && (
				this.item.getComputationBaseReferencePeriod() == null 
				|| this.item.getComputationBaseReferencePeriod().getValue() == null
				|| this.item.getComputationBaseReferencePeriod().getValue().isEmpty())) {
			logger.warn("A percent is set, but no computation base reference period is defined!");
			return false;
		}

		return true;
	}

    private TimePeriod getChargeTimePeriod(OffsetDateTime time) {

		if (this.item == null) {
			logger.error("PlanItem is null, cannot determine time period");
			return null;
		}

		SubscriptionTimeHelper sth = new SubscriptionTimeHelper(this.getSubscription());
		TimePeriod tp = null;

		Price referencePrice = this.item.getReferencePrice();
		logger.debug("referencePrice is {}", referencePrice.getName());
		logger.debug("referencePrice.type is {}", referencePrice.getType());
		if (referencePrice.getType() != null) {
			switch (referencePrice.getType()) {
				case RECURRING_PREPAID:
					logger.debug("Computing charge period for RECURRING_PREPAID price type");
					tp = sth.getChargePeriodAt(time, referencePrice);
					break;
				case RECURRING_POSTPAID:
					logger.debug("Computing charge period for RECURRING_POSTPAID price type");
					tp = sth.getChargePeriodAt(time, referencePrice);
					break;
				case ONE_TIME_PREPAID:
					logger.debug("Computing charge period for ONE_TIME_PREPAID price type");
					TimePeriod currentPeriod = sth.getSubscriptionPeriodAt(time);
					OffsetDateTime startDate = this.getSubscription().getStartDate();
					if (currentPeriod.getStartDateTime().equals(startDate)) {
						tp = currentPeriod;
					} else {
						logger.debug("Current period not match with startDate. It has probably already been calculated");
						tp = null;
					}
					break;
				default:
					throw new IllegalArgumentException("Unsupported price type: " + referencePrice.getType());
			}
//		} else {
//			// TODO: GET PARENT TYPE FROM PRICE ?? - discuss if it can be removed
			// THIS shouldn't be needed...as the price should already provide all needed information recursively.
//            tp = sth.getSubscriptionPeriodAt(time);
//			tp = this.getTimePeriod(referencePrice.getParentPrice(), time);
		}

		if(tp!=null)
			logger.debug("Computed time period for price {} - tp: {} - {}", this.item.getName(), tp.getStartDateTime(), tp.getEndDateTime());

		return tp;
	}

	protected TimePeriod getComputationTimePeriod(OffsetDateTime time) {
		// extract the keyword of reference period for the computation base
		String computationPeriodKeyword = null;
		if(this.item.getComputationBaseReferencePeriod() != null) 
			computationPeriodKeyword = this.item.getComputationBaseReferencePeriod().getValue();
		if(computationPeriodKeyword==null) {
			logger.debug("No reference period specified. ComputationBaseReferencePeriod is null");
			return null;
		}
		SubscriptionTimeHelper helper = new SubscriptionTimeHelper(this.getSubscription());
		TimePeriod computationPeriod = helper.getCustomPeriod(time, this.item.getReferencePrice(), computationPeriodKeyword);
		return computationPeriod;
	}

	protected TimePeriod getApplicableTimePeriod(OffsetDateTime time) {
		// extract the keyword of reference period for the applicable base
		String applicablePeriodKeyword = null;
		if(this.item.getApplicableBaseReferencePeriod() != null) 
			applicablePeriodKeyword = this.item.getApplicableBaseReferencePeriod().getValue();
		if(applicablePeriodKeyword==null) {
			logger.debug("No reference period specified. ApplicableBaseReferencePeriod is null");
			return null;
		}
		SubscriptionTimeHelper helper = new SubscriptionTimeHelper(this.getSubscription());
		TimePeriod applicablePeriod = helper.getCustomPeriod(time, this.item.getReferencePrice(), applicablePeriodKeyword);
		return applicablePeriod;
	}

	/*
    private TimePeriod getApplicableTimePeriod(OffsetDateTime time) {        
        // TODO: refactor this method to be more generic

        if (this.item == null) {
            logger.error("Item is null, cannot determine time period");
            return null;
        }

        SubscriptionTimeHelper sth = new SubscriptionTimeHelper(this.getSubscription());
        TimePeriod tp = null;

        if (this.item.getApplicableBaseReferencePeriod() != null) {
            String ref = this.item.getApplicableBaseReferencePeriod().getValue();

            if ("PREVIOUS_SUBSCRIPTION_PERIOD".equalsIgnoreCase(ref)) {
                tp = sth.getSubscriptionPeriodAt(time);

            } else if ("FIRST_3_CHARGE_PERIODS".equalsIgnoreCase(ref)) {
				// FIXME: this should be covered by the following 'else' which should be more generic
                OffsetDateTime start = subscription.getStartDate();
                OffsetDateTime endThree = start.plusMonths(3);

                // Creo un periodo "fittizio" che rappresenta i primi 3 mesi
                TimePeriod firstThreeMonths = new TimePeriod();
                firstThreeMonths.setStartDateTime(start);
                firstThreeMonths.setEndDateTime(endThree);

                TimePeriod firstChargePeriod = sth.getChargePeriodAt(time, this.item.getReferencePrice());

                // Verifico che il tempo richiesto ricada dentro i primi 3 mesi
                if (!time.isBefore(start) && !time.isAfter(endThree)) {
                    tp = firstChargePeriod;
                } else {
                    logger.debug(
                            "Time '{}' non ricade nei primi 3 mesi [{} - {}], returning null",
                            time, start, endThree
                    );
                    return null;
                }
            } else {
				TimePeriod custom = sth.getCustomPeriod(time, this.item.getReferencePrice(), ref);
				if (custom != null
						&& !time.isBefore(custom.getStartDateTime())
						&& !time.isAfter(custom.getEndDateTime())) {
					tp = sth.getChargePeriodAt(time, this.item.getReferencePrice());
				} else {
					logger.debug("Time '{}' non ricade nel periodo custom '{}': [{} - {}], returning null",
							time, ref,
							custom != null ? custom.getStartDateTime() : "null",
							custom != null ? custom.getEndDateTime() : "null");
					tp = null;
				}
            }

        } else {
            // Default: periodo di subscription corrente
            tp = sth.getSubscriptionPeriodAt(time);
        }

        return tp;
    }
	*/

	private Double getApplicableValue(String subscriberId, TimePeriod tp) {

		// TODO: the applicable base can also be 'parent-price'. This is not currently supported.
		// In general, the context is not considered here. Shuould it?

		if (this.item.getApplicableBase() == null || this.item.getApplicableBase().isEmpty()) {
			return null;
		}

		try {
			TimePeriod applicabilityTimePeriod = this.getApplicableTimePeriod(tp.getEndDateTime());

			if(applicabilityTimePeriod!=null) {
				Double applicableValue = this.metricsRetriever.computeValueForKey(this.item.getApplicableBase(), subscriberId, applicabilityTimePeriod);
				return applicableValue;
			} else {
				logger.debug("There's no applicableTimePeriod for {}. No applicableValue can be computed", this.item.getName());
				return null;
			}
		} catch (Exception e) {
			logger.error("Error computing applicable value for base '{}': {}", this.item.getApplicableBase(), e.getMessage(), e);
			return null;
		}
	}

	protected Subscription getSubscription() {
        return this.subscription;
    }

    public Map<String, String> getCalculatorContext() {
		return calculatorContext;
	}

	public void setCalculatorContext(Map<String, String> calculatorContext) {
		this.calculatorContext = calculatorContext;
	}

	public void addCalculatorContext(String key, String value) {
		this.calculatorContext.put(key, value);
	}

}
