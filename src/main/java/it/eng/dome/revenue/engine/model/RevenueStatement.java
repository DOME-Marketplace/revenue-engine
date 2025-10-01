package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class RevenueStatement {

    private Subscription subscription;
    private TimePeriod period;
    private List<RevenueItem> revenueItems;

    public RevenueStatement() {
		this.revenueItems = new ArrayList<>();
    }
    
    public RevenueStatement(Subscription subscription, TimePeriod period) {
        this();
        this.subscription = subscription;
        this.period = period;
    }

    public RevenueStatement(Subscription subscription, TimePeriod period, List<RevenueItem> revenueItems) {
        this(subscription, period);
        this.revenueItems.clear();
        this.revenueItems.addAll(revenueItems);
	}
    
    @JsonProperty("description")
    public String getDescription() {
        String name = "Revenue Statement for " + subscription.getSubscriberId() + "; plan " + subscription.getPlan().getName() + " from " + period.getStartDateTime() + " to " + period.getEndDateTime();
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

    public List<RevenueItem> getRevenueItems() {
        return revenueItems;
    }

    public void setRevenueItems(List<RevenueItem> revenueItems) {
        this.revenueItems = revenueItems;
    }

    public void addRevenueItem(RevenueItem item) {
        if (this.revenueItems == null) {
            this.revenueItems = new ArrayList<>();
        }
        this.revenueItems.add(item);
    }

    /**
     * Replace the current set of revenueItems with a new set where each root subtree contains items with the same charge time
     * So that it will be easier to create AppliedCustomerBillingRates
     */
    public void clusterizeItems() {
        List<RevenueItem> newItems = new ArrayList<>();
        // replace items with filtered ones
        if(this.getRevenueItems()!=null) {
            for(RevenueItem item : this.getRevenueItems()) {
                for(OffsetDateTime chargeTime : item.extractChargeTimes()) {
                    newItems.add(item.getFilteredClone(chargeTime));
                }
            }
        }
        this.setRevenueItems(newItems);
    }

    @JsonProperty("estimated")
    public Boolean isEstimated() {
        if(this.revenueItems!=null) {
            for(RevenueItem i:revenueItems) {
                if(i.isEstimated())
                    return true;
            }
        }
        return false;
    }

}
