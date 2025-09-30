package it.eng.dome.revenue.engine.service.compute2;

import java.time.OffsetDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import it.eng.dome.revenue.engine.model.PlanItem;
import it.eng.dome.revenue.engine.model.Price;
import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.revenue.engine.model.SubscriptionTimeHelper;
import it.eng.dome.revenue.engine.service.MetricsRetriever;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public abstract class AbstractCalculator implements Calculator {

    private static final Logger logger = LoggerFactory.getLogger(AbstractCalculator.class);

    @Autowired
	MetricsRetriever metricsRetriever;

    protected PlanItem item;
    private Subscription subscription;

    public AbstractCalculator(Subscription subscription, PlanItem item) {
        this.subscription = subscription;
        this.item = item;
    }

	public final RevenueItem compute(TimePeriod timePeriod, Map<String, Double> computeContext) {

		// check if it's to skip or not
		if(!this.checkIgnoreConditions(timePeriod))
			return null;

		// check if it has to be zeroed (although we keep the structure)
		boolean zeroIt = this.checkZeroIt(timePeriod);

		// do build the structure and value
		RevenueItem outRevenueItem = this.doCompute(timePeriod, computeContext);

		// zero the item, if needed
		if(outRevenueItem!=null && zeroIt)
			outRevenueItem.zeroAmountsRecursively();

		// variable vallues for future items lead to estimaed revenueItems
		if (this.item.isVariable() && outRevenueItem.getChargeTime().isAfter(OffsetDateTime.now())) {
			outRevenueItem.setEstimated(true);
		} else {
			outRevenueItem.setEstimated(false);
		}

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
    private boolean checkIgnoreConditions(TimePeriod timePeriod) {

		// check the charge time period
		TimePeriod tp = this.getChargeTimePeriod(timePeriod.getStartDateTime().plusSeconds(1));
		if (tp == null || !tp.getStartDateTime().equals(timePeriod.getStartDateTime())
				|| !tp.getEndDateTime().equals(timePeriod.getEndDateTime())) {
			return false;
		}

		// check it's not to ignore
	    if ("true".equalsIgnoreCase(this.item.getIgnore())) {
	        logger.info("Ignoring price/discount {} based on ignore flag {}", this.item.getName(), this.item.getIgnore());
			return false;
	    }

        return true;
    }

	private boolean checkZeroIt(TimePeriod timePeriod) {
		// FIXME: should the check below be reeally here or in the "checkIgnore"?

		boolean zeroIt = false;
		
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
			//return null;
			zeroIt = true;
		}

		// now also check the 'ignorePeriod' property. Resolve it and check if the period is affected.
		// FIXME: same considerations as above
		SubscriptionTimeHelper sth = new SubscriptionTimeHelper(this.getSubscription());
		if(this.item.getIgnorePeriod()!=null) {
		    TimePeriod tp = sth.getCustomPeriod(null, (Price)this.item, ((Price)this.item).getIgnorePeriod().getValue());
			if(tp!=null) {
				logger.debug("For this price, ignoring the period {} - {}", tp.getStartDateTime(), tp.getEndDateTime());
				if(timePeriod.getStartDateTime().isBefore(tp.getEndDateTime())) {
					logger.debug("Ignoring the price entirely as it sarts within the period");
//					return null;
					zeroIt = true;
				}
			}
		}
		
		return zeroIt;
		
	}


	/**
	 * Make sure that properties for applicability are set correctly and that conditions are satisfied
	 * @param tp
	 * @return
	 */
    protected boolean checkApplicability(TimePeriod timePeriod) {

		String subscriberId = this.getSubscription().getSubscriberId();

		Double applicableValue = this.getApplicableValue(subscriberId, timePeriod);
		if (applicableValue == null) {
			// if not exists an applicable or an computation then we had only amount price
			return true;
		}

		// if value in range then computation
		if (this.item.getApplicableBaseRange().inRange(applicableValue)) {
			logger.info("Applicable value: {}, for price: {}, in tp: {} - {}", applicableValue, this.item.getName(), timePeriod.getStartDateTime(), timePeriod.getEndDateTime());
			return true;
		}
		return false;
	}

    /**
     * Make sure that properties for computation are correctly set and are available
     * @param tp
     * @return
     */
    protected boolean checkComputability(TimePeriod tp) {
		// make sure a computationBase is set (TODISCUSS: is this true? maybe a fixed amount do)
		if (this.item.getComputationBase() == null || this.item.getComputationBase().isEmpty()) {
			logger.debug("No computation base defined!");
			return false;
		}
		// TODO: refactor these checks... a mix of the two with the above
		// FIXME: for bundles this is not needed
		if (this.item.getPercent() == null && this.item.getAmount() == null) {
			logger.debug("Neither percent nor amount defined for computation!");
			return false;
		}
		// TODO: check the reference period is set
		return true;
	}

    private TimePeriod getChargeTimePeriod(OffsetDateTime time) {
		if (this.item == null) {
			logger.error("Price is null, cannot determine time period");
			return null;
		}

		SubscriptionTimeHelper sth = new SubscriptionTimeHelper(this.getSubscription());
		TimePeriod tp = new TimePeriod();

		Price referencePrice = this.item.getReferencePrice();
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

	private Double getApplicableValue(String subscriberId, TimePeriod tp) {
		if (this.item.getApplicableBase() == null || this.item.getApplicableBase().isEmpty()) {
			return null;
		}

		try {
			TimePeriod actualTp = tp;
			actualTp = this.getApplicableTimePeriod(tp.getEndDateTime());

			/*
			String referencePeriod = this.item.getApplicableBaseReferencePeriod().getValue();
			if (referencePeriod != null) {
				SubscriptionTimeHelper helper = new SubscriptionTimeHelper(this.getSubscription());

				if ("PREVIOUS_SUBSCRIPTION_PERIOD".equals(referencePeriod)) {
					actualTp = helper.getPreviousSubscriptionPeriod(tp.getEndDateTime());
				} else if ((referencePeriod.startsWith("PREVIOUS_") || referencePeriod.startsWith("LAST_"))
						&& referencePeriod.endsWith("_CHARGE_PERIODS")) {
					actualTp = helper.getCustomPeriod(tp.getEndDateTime(), this.item.getReferencePrice(), referencePeriod);

					if (actualTp == null) {
						logger.debug("Could not compute custom period for reference: {}", referencePeriod);
						return null;
					}

					logger.debug("Using custom period for {}: {} - {}, based on reference: {}", referencePeriod,
							actualTp.getStartDateTime(), actualTp.getEndDateTime(), referencePeriod);
				}
			}
			*/

			Double applicableValue = this.metricsRetriever.computeValueForKey(this.item.getApplicableBase(), subscriberId, actualTp);

			return applicableValue;
		} catch (Exception e) {
			logger.error("Error computing applicable value for base '{}': {}", this.item.getApplicableBase(),
					e.getMessage(), e);
			return null;
		}
	}

	protected Subscription getSubscription() {
        return this.subscription;
    }

}
