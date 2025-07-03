package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;

public class SubscriptionTimeHelper {

    private Subscription subscription;

    public SubscriptionTimeHelper(Subscription subscription) {
        this.subscription = subscription;
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
            System.out.println("Checking period: " + start + " - " + end);
            if(!time.isBefore(start) && time.isBefore(end)) {
                return new TimePeriod(start, end);
            }
            start = end;
        }
        return null;
    }

    public TimePeriod getChargePeriodByOffset(OffsetDateTime time, Price price, Integer howManyPeriods) {
        OffsetDateTime shiftedTime = this.rollChargePeriod(time, price, howManyPeriods);
        System.out.println("Shifted time: " + shiftedTime);
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
        switch(pType) {
            case DAY:
                return time.plusDays(pLength * howManyPeriods);
            case WEEK:
                return time.plusWeeks(pLength * howManyPeriods);
            case MONTH:
                return time.plusMonths(pLength * howManyPeriods);
            case YEAR:
                return time.plusYears(pLength * howManyPeriods);
        }
        throw new IllegalArgumentException("Unknown RecurringPeriod type: " + pType);
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
        }
        throw new IllegalArgumentException("Unknown RecurringPeriod type: " + pType);
    }

    public static void main(String[] args) throws Exception{

        // create a fake subscription plan
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setContractDurationPeriodType(RecurringPeriod.YEAR);
        plan.setContractDurationLength(1);

        Subscription subscription = new Subscription();
        subscription.setPlan(plan);
        subscription.setStartDate(OffsetDateTime.now().minusMonths(8)); // start 8 months ago
        System.out.println("Subscription start date: " + subscription.getStartDate());
        // create an helper
        SubscriptionTimeHelper helper = new SubscriptionTimeHelper(subscription);

        // go ahead one subscription period from now
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime rolledTime = helper.rollSubscriptionPeriod(now, 2);
        System.out.println(rolledTime);

        // now create a price with a charge period
        Price price = new Price();
        price.setRecurringChargePeriodType(RecurringPeriod.MONTH);
        price.setRecurringChargePeriodLength(1);

        Thread.sleep(1000);
        now = OffsetDateTime.now();

        TimePeriod chargePeriod = helper.getChargePeriodByOffset(now, price, 18);
        System.out.println(chargePeriod.getFromDate());
        System.out.println(chargePeriod.getToDate());
    }

}
