package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;

public class SubscriptionTimeHelper {

    private SubscriptionActive subscription;
    private Price price;

    public SubscriptionTimeHelper(SubscriptionActive subscription, Price price) {
        this.subscription = subscription;
        this.price = price;
    }

    public SubscriptionTimeHelper(SubscriptionActive subscription) {
        this(subscription, null);
    }

    // compute the subscription period at a given time
    // if the time is before the start of the subscription, return null
    // if the time is after the end of the subscription, return null
    // TODO: in any case, clip to the start of the subscription
    public TimePeriod getSubscriptionPeriodAt(OffsetDateTime time) {
        // TODO: impement me
        return null;
    }

    // compute the current subscription period
    public TimePeriod getCurrentSubscriptionPeriod() {
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



}
