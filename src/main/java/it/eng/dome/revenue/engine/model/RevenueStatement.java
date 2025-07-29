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

    public RevenueStatement() {}
    
    public RevenueStatement(Subscription subscription, TimePeriod period) {
        this.subscription = subscription;
        this.period = period;
    }

    public RevenueStatement(Subscription subscription, TimePeriod period, List<RevenueItem> revenueItem) {
    	this.subscription = subscription;
        this.period = period;
		this.revenueItems = new ArrayList<>(revenueItem);
	}
    
    @JsonProperty("description")
    public String getDescription() {
        String name = "Revenue Statement for " + subscription.getBuyerId() + "; plan " + subscription.getPlan().getName() + " from " + period.getStartDateTime() + " to " + period.getEndDateTime();
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

    public void clusterizeItems() {
        List<RevenueItem> newItems = new ArrayList<>();
        // replace items with filtered ones
        for(RevenueItem item : this.getRevenueItems()) {
            for(OffsetDateTime chargeTime : item.extractChargeTimes()) {
                newItems.add(item.getFilteredClone(chargeTime));
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
