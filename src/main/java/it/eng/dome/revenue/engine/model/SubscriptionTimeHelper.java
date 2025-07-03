package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;

public class SubscriptionTimeHelper {

    private Subscription subscription;
    private Price price;

    public SubscriptionTimeHelper(Subscription subscription, Price price) {
        this.subscription = subscription;
        this.price = price;
    }

    public SubscriptionTimeHelper(Subscription subscription) {
        this(subscription, null);
    }

    // Compute the subscription period at a given time
    public TimePeriod getSubscriptionPeriodAt(OffsetDateTime time) {

        // if the time is before the start of the subscription, return null
        if(time.isBefore(this.subscription.getStartDate()))
            return null;

        // iterate over the subscription periods, untile found one that contains the time
        OffsetDateTime start = this.subscription.getStartDate();
        OffsetDateTime now = OffsetDateTime.now();
        while(start.isBefore(now)) {
            OffsetDateTime end = this.rollSubscriptionPeriod(start, 1);
            if(time.isAfter(start) && time.isBefore(end)) {
                return new TimePeriod(start, end);
            }
        }
        return null;
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

    // compute the current subscription period
    public TimePeriod getCurrentSubscriptionPeriod() {
        // FIXME: remove the following. Just to avoid PMD complaining about unused variables
        this.price.getRecurringChargePeriodLength();

        return this.getSubscriptionPeriodAt(OffsetDateTime.now());
    }

    // compute the previous subscription period wrt given time
    // if the period is before the start of the subscription, return null
    // TODO: in any case, clip to the start of the subscription
    public TimePeriod getPreviousSubscriptionPeriod(OffsetDateTime time) {
        OffsetDateTime onePeriodAgo = time.minusYears(1); // FIXME: go back to the previous period. Use contractual duration length, etc...
        // TODO: if before the start of the subscription, return null
        return this.getSubscriptionPeriodAt(onePeriodAgo);
    }

    // compute the previous subscription period
    // if the current time is before the start of the subscription, return null
    public TimePeriod getPreviousSubscriptionPeriod() {
        return this.getPreviousSubscriptionPeriod(OffsetDateTime.now());
    }
    // compute the charge period at a given time
    // considering the given price (with chargeperiod, offset and length)
    public TimePeriod getChargePeriod(OffsetDateTime time) {
        return null; // TODO: implement me
    }

    public TimePeriod getCurrentChargePeriod() {
        return this.getChargePeriod(OffsetDateTime.now());
    }

    public TimePeriod getPreviousChargePeriod(OffsetDateTime time) {
        OffsetDateTime oneChargePeriodAgo = time.minusYears(1); // FIXME: go back to the previous period. Use charge duration, etc...
        return this.getChargePeriod(oneChargePeriodAgo);
    }

    public TimePeriod getPreviousChargePeriod() {
        return this.getPreviousChargePeriod(OffsetDateTime.now());
    }

    public static void main(String[] args) {

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setContractDurationPeriodType(RecurringPeriod.YEAR);
        plan.setContractDurationLength(10);

        SubscriptionActive subscription = new SubscriptionActive();
        subscription.setPlan(plan);

        SubscriptionTimeHelper helper = new SubscriptionTimeHelper(subscription);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime end = helper.rollSubscriptionPeriod(now, 1);
        System.out.println(end);
    }

}
