package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private Set<TimePeriod> getChargePeriodTimes(Price price) {
        // the start date of the subscription
        OffsetDateTime start = this.subscription.getStartDate();
        Set<TimePeriod> chargePeriodTimes = new TreeSet<>();
        if(price.getIsBundle()) {
            for(Price p: price.getPrices()) {
                chargePeriodTimes.addAll(this.getChargePeriodTimes(p));
            }
        } else {
            // iterate over the charge periods, until reaching the current time
            // or a year in the future
            OffsetDateTime stopAt = OffsetDateTime.now().plusYears(1);
            while(!start.isAfter(stopAt)) {
                OffsetDateTime end = this.rollChargePeriod(start, price, 1);
                chargePeriodTimes.add(new TimePeriod(start, end));
                start = end;
            }
            return chargePeriodTimes;
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
                return new TimePeriod(start, end);
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
                return new TimePeriod(start, end);
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

    public static void main(String[] args) throws Exception{

        final Logger logger = LoggerFactory.getLogger(SubscriptionTimeHelper.class);

        // create a fake subscription plan
        SubscriptionPlan plan = new SubscriptionPlan();
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
        logger.debug(chargePeriod.getFromDate().toString());
        logger.debug(chargePeriod.getToDate().toString());

        Set<TimePeriod> chargePeriods = helper.getChargePeriodTimes();
        for(TimePeriod t : chargePeriods) {
            logger.debug("Charge period: " + t);
        }
    }

}
