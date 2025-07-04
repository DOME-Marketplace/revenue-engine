package it.eng.dome.revenue.engine.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RevenueStatement {

    private Subscription subscription;
    private TimePeriod period;
    private RevenueItem revenueItem;

    public RevenueStatement(Subscription subscription, TimePeriod period) {
        this.subscription = subscription;
        this.period = period;
    }

    @JsonProperty("description")
    public String getDescription() {
        String name = "Revenue Statement for " + "<unknown>" + "; plan " + subscription.getPlan().getName() + " from " + period.getFromDate() + " to " + period.getToDate();
        return name;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public TimePeriod getPeriod() {
        return period;
    }

    public void setPeriod(TimePeriod period) {
        this.period = period;
    }

    public RevenueItem getRevenueItem() {
        return revenueItem;
    }

    public void setRevenueItem(RevenueItem revenueItem) {
        this.revenueItem = revenueItem;
    }

}
