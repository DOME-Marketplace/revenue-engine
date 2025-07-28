package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

class TimePeriodComparator implements Comparator<TimePeriod> {

    @Override
    public int compare(TimePeriod o1, TimePeriod o2) {
        int result = o1.getStartDateTime().compareTo(o2.getStartDateTime());
        if (result != 0)
            return result;  // Compare by start date    
        return o1.getEndDateTime().compareTo(o2.getEndDateTime());


    }
}

public class SubscriptionTimeHelper {

    private final Logger logger = LoggerFactory.getLogger(SubscriptionTimeHelper.class);

    private Subscription subscription;

    public SubscriptionTimeHelper(Subscription subscription) {
        this.subscription = subscription;
    }

    /**
     * Starting from the subscription start date, this method returns a list of all the charge period times
     * for the subscription, based on the price's recurring charge period type and length.
     * @return
     */
    public Set<TimePeriod> getChargePeriodTimes() {
        if(this.subscription != null && this.subscription.getPlan() != null && this.subscription.getPlan().getPrice() != null) {
            return this.getChargePeriodTimes(this.subscription.getPlan().getPrice());
        } else {
            return new HashSet<>();
        }
    }

    public Set<TimePeriod> getBillingTimePeriods() {
        Set<TimePeriod> billingPeriodTimes = new TreeSet<>(new TimePeriodComparator());       
        if(this.subscription != null && this.subscription.getPlan() != null) {
            OffsetDateTime start = this.subscription.getStartDate();
            // build a preview for the next 1 year
            OffsetDateTime stopAt = OffsetDateTime.now().plusYears(1);
            while(!start.isAfter(stopAt)) {
                OffsetDateTime end = this.rollBillPeriod(start, 1);
                TimePeriod tp = new TimePeriod();
                tp.setStartDateTime(start);
                // apply the modifier, if any.
                end = this.applyModifier(end, this.subscription.getPlan().getBillingPeriodEnd());
                tp.setEndDateTime(end.minusSeconds(1));
                billingPeriodTimes.add(tp);
                start = end;
            }
        }
        return billingPeriodTimes;
    }

    private OffsetDateTime applyModifier(OffsetDateTime time, String modifier) {
        if(time==null || modifier==null)
            return time;
        if("LAST_DAY_OF_CALENDAR_MONTH".equals(modifier)) {
            // go to the start of current month
            OffsetDateTime modifiedTime = OffsetDateTime.of(time.getYear(), time.getMonthValue(), 1, 0, 0, 0, 0, time.getOffset());
            // advance one month
            modifiedTime = modifiedTime.plusMonths(1);
            return modifiedTime;
        } else {
            logger.warn("unknown modifier {}. Returning unchanged time");
            return time;
        }

    }

    private Set<TimePeriod> getChargePeriodTimes(Price price) {
        Set<TimePeriod> chargePeriodTimes = new TreeSet<>(new TimePeriodComparator());       
        if(price.getIsBundle()) {
            for(Price p: price.getPrices()) {
                chargePeriodTimes.addAll(this.getChargePeriodTimes(p));
            }
        } else {
            // the start date of the subscription
            OffsetDateTime start = this.subscription.getStartDate();
            if(PriceType.ONE_TIME_PREPAID.equals(price.getType())) {
                TimePeriod tp = new TimePeriod();
                // if the price is a one-time prepaid, return only one period for the first subscription period
                OffsetDateTime end = this.rollSubscriptionPeriod(start, 1);
                tp.setStartDateTime(start);
                tp.setEndDateTime(end);
                chargePeriodTimes.add(tp);
                logger.debug("One-time prepaid price, returning single charge period: " + tp + " for price: " + price.getName());
            } else {
                // iterate over the charge periods, until reaching the current time
                // or a year in the future
                OffsetDateTime stopAt = OffsetDateTime.now();
                while(!start.isAfter(stopAt)) {
                    OffsetDateTime end = this.rollChargePeriod(start, price, 1);
                    TimePeriod tp = new TimePeriod();
                    tp.setStartDateTime(start);
                    tp.setEndDateTime(end);
                    chargePeriodTimes.add(tp);
                    start = end;
                    logger.debug("Adding charge period: " + tp + " for price: " + price.getName());
                }
            }
        }
        return chargePeriodTimes;
    }

    // Compute the subscription period at a given time
    public TimePeriod getSubscriptionPeriodAt(OffsetDateTime time) {

        // if the time is before the start of the subscription, return null
        if(time.isBefore(this.subscription.getStartDate()))
            return null;

        // iterate over the subscription periods, until found one that contains the time
        OffsetDateTime start = this.subscription.getStartDate();
        while(!start.isAfter(time)) {
            OffsetDateTime end = this.rollSubscriptionPeriod(start, 1);
            if(!time.isBefore(start) && time.isBefore(end)) {
            	TimePeriod tp = new TimePeriod();
            	tp.setStartDateTime(start);
            	tp.setEndDateTime(end);
                return tp;
            }
            start = end;
        }
        return null;
    }

    public TimePeriod getSubscriptionPeriodByOffset(OffsetDateTime time, Integer howManyPeriods) {
        OffsetDateTime shiftedTime = this.rollSubscriptionPeriod(time, howManyPeriods);
        return this.getSubscriptionPeriodAt(shiftedTime);
    }

    // compute the previous subscription period wrt given time
    public TimePeriod getPreviousSubscriptionPeriod(OffsetDateTime time) {
        return this.getSubscriptionPeriodByOffset(time, -1);
    }

        // compute the previous subscription period wrt given time
    public TimePeriod getNextSubscriptionPeriod(OffsetDateTime time) {
        return this.getSubscriptionPeriodByOffset(time, 1);
    }

    public TimePeriod getCustomPeriod(OffsetDateTime time, Price price, String keyword) {
        // if the keyword is null or empty, return null
        if(keyword != null && !keyword.isEmpty()) {
            Pattern p = Pattern.compile("^(LAST|PREVIOUS)_(\\d+)_BILLING_PERIODS$");
            var matcher = p.matcher(keyword);
            if(matcher.matches()) {
                Integer howManyPeriods = Integer.parseInt(matcher.group(2));
                String timeWindowEndType = matcher.group(1);
                TimePeriod startPeriod = null;
                TimePeriod endPeriod = null;
                if("LAST".equals(timeWindowEndType)) {
                    startPeriod = this.getChargePeriodByOffset(time, price, -howManyPeriods+1);
                    endPeriod = this.getChargePeriodAt(time, price);
                } else if("PREVIOUS".equals(timeWindowEndType)) {
                    startPeriod = this.getChargePeriodByOffset(time, price, -howManyPeriods);
                    endPeriod = this.getPreviousChargePeriod(time, price);
                }
                if(startPeriod==null) {
                    // the period is before the subscription, constraining to the start of the subscription
                    startPeriod = this.getChargePeriodAt(this.subscription.getStartDate(), price);
                }
                if(startPeriod != null && endPeriod != null) {
                	TimePeriod tp = new TimePeriod();
                	tp.setStartDateTime(startPeriod.getStartDateTime());
                	tp.setEndDateTime(endPeriod.getEndDateTime());
                    return tp;
                }
            }
        }
        return null;
    }

    // Compute the subscription period at a given time
    public TimePeriod getChargePeriodAt(OffsetDateTime time, Price price) {

        // if the time is before the start of the subscription, return null
        if(time.isBefore(this.subscription.getStartDate()))
            return null;

        // iterate over the charge periods, until found one that contains the time
        OffsetDateTime start = this.subscription.getStartDate();
        while(!start.isAfter(time)) {
            OffsetDateTime end = this.rollChargePeriod(start, price, 1);
            logger.debug("Checking period: " + start + " - " + end);
            if(!time.isBefore(start) && time.isBefore(end)) {
            	TimePeriod tp = new TimePeriod();
            	tp.setStartDateTime(start);
            	tp.setEndDateTime(end);
                return tp;
            }
            start = end;
        }
        return null;
    }

    public TimePeriod getChargePeriodByOffset(OffsetDateTime time, Price price, Integer howManyPeriods) {
        OffsetDateTime shiftedTime = this.rollChargePeriod(time, price, howManyPeriods);
        logger.debug("Shifted time: " + shiftedTime);
        return this.getChargePeriodAt(shiftedTime, price);
    }

    public TimePeriod getCurrentChargePeriod(Price price) {
        return this.getChargePeriodAt(OffsetDateTime.now(), price);
    }

    public TimePeriod getPreviousChargePeriod(OffsetDateTime time, Price price) {
        return this.getChargePeriodByOffset(time, price, -1);
    }

    public TimePeriod getNextChargePeriod(OffsetDateTime time, Price price) {
        return this.getChargePeriodByOffset(time, price, 1);
    }

    private OffsetDateTime rollSubscriptionPeriod(OffsetDateTime time, int howManyPeriods) {
        // retrive subscriptino length unit
        RecurringPeriod pType = this.subscription.getPlan().getContractDurationPeriodType();
        // retrieve subscription length
        Integer pLength = this.subscription.getPlan().getContractDurationLength();
        // increase according to pType and pLength and howManyPeriods
        pType = (pType != null) ? pType : RecurringPeriod.YEAR; // default to YEAR if not set
        pLength = (pLength != null) ? pLength : 1; // default to 1 if not set
        switch(pType) {
            case DAY:
                return time.plusDays(pLength * howManyPeriods);
            case WEEK:
                return time.plusWeeks(pLength * howManyPeriods);
            case MONTH:
                return time.plusMonths(pLength * howManyPeriods);
            case YEAR:
                return time.plusYears(pLength * howManyPeriods);
            default:
                throw new IllegalArgumentException("Unknown RecurringPeriod type: " + pType);
        }
    }

    private OffsetDateTime rollChargePeriod(OffsetDateTime time, Price price, int howManyPeriods) {
        // retrive subscriptino length unit
        RecurringPeriod pType = price.getRecurringChargePeriodType();
        // retrieve subscription length
        Integer pLength = price.getRecurringChargePeriodLength();
        // ensure the two above are set
        if(pType == null || pLength == null) {
            throw new IllegalArgumentException("Price does not have a valid recurring charge period type or length");
        }
        // increase according to pType and pLength and howManyPeriods
        switch(pType) {
            case DAY:
                return time.plusDays(pLength * howManyPeriods);
            case WEEK:
                return time.plusWeeks(pLength * howManyPeriods);
            case MONTH:
                return time.plusMonths(pLength * howManyPeriods);
            case YEAR:
                return time.plusYears(pLength * howManyPeriods);
            default:
                throw new IllegalArgumentException("Unknown RecurringPeriod type: " + pType);
        }
    }

    private OffsetDateTime rollBillPeriod(OffsetDateTime time, int howManyPeriods) {
        // retrive subscriptino length unit
        RecurringPeriod pType = this.subscription.getPlan().getBillingPeriodType();
        // retrieve subscription length
        Integer pLength = this.subscription.getPlan().getBillingPeriodLength();
        // ensure the two above are set
        if(pType == null || pLength == null) {
            throw new IllegalArgumentException("Subscription does not have a valid recurring billing period type or length");
        }
        // increase according to pType and pLength and howManyPeriods
        switch(pType) {
            case DAY:
                return time.plusDays(pLength * howManyPeriods);
            case WEEK:
                return time.plusWeeks(pLength * howManyPeriods);
            case MONTH:
                return time.plusMonths(pLength * howManyPeriods);
            case YEAR:
                return time.plusYears(pLength * howManyPeriods);
            default:
                throw new IllegalArgumentException("Unknown RecurringPeriod type: " + pType);
        }
    }


    public static void main(String[] args) throws Exception{

        final Logger logger = LoggerFactory.getLogger(SubscriptionTimeHelper.class);

        // create a fake subscription plan
        Plan plan = new Plan();
        plan.setContractDurationPeriodType(RecurringPeriod.YEAR);
        plan.setContractDurationLength(1);

        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStartDate(OffsetDateTime.now().minusYears(8)); // start 8 months ago
        logger.debug("Subscription start date: " + subscription.getStartDate());
        // create an helper
        SubscriptionTimeHelper helper = new SubscriptionTimeHelper(subscription);

        // go ahead one subscription period from now
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime rolledTime = helper.rollSubscriptionPeriod(now, 2);
        logger.debug(rolledTime.toString());

        Price bundle = new Price();
        bundle.setName("Bundle Price");
        bundle.setIsBundle(true);

        // now create a price with a charge period
        Price price = new Price();
        price.setRecurringChargePeriodType(RecurringPeriod.YEAR);
        price.setRecurringChargePeriodLength(1);

        Price anotherPrice = new Price();
        anotherPrice.setRecurringChargePeriodType(RecurringPeriod.MONTH);
        anotherPrice.setRecurringChargePeriodLength(13);

        List<Price> prices = List.of(price, anotherPrice);
        bundle.setPrices(prices);
        plan.setPrice(bundle);

        Thread.sleep(100);
        now = OffsetDateTime.now();

        TimePeriod chargePeriod = helper.getChargePeriodByOffset(now, price, 18);
        logger.debug(chargePeriod.getStartDateTime().toString());
        logger.debug(chargePeriod.getEndDateTime().toString());

        Set<TimePeriod> chargePeriods = helper.getChargePeriodTimes();
        for(TimePeriod t : chargePeriods) {
            logger.debug("Charge period: " + t);
        }

        TimePeriod tp = helper.getCustomPeriod(now, price, "PREVIOUS_1_BILLING_PERIODS");
        logger.debug("Custom period: " + tp);

        logger.debug(helper.getPreviousChargePeriod(now, price).toString());
        logger.debug(helper.getCurrentChargePeriod(price).toString());
    }

}
