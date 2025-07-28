package it.eng.dome.revenue.engine.model;

import java.util.ArrayList;
import java.util.List;

import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class SimpleBill {

    private TimePeriod period;
    private List<RevenueItem> revenueItems;

    public SimpleBill() {
        this.revenueItems = new ArrayList<>();
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
        for(RevenueItem i:revenueItems)
            this.addRevenueItem(i);
    }

    public void addRevenueItem(RevenueItem item) {
        if(this.isItemInPeriod(item))
            this.revenueItems.add(item);
    }

    public boolean isItemInPeriod(RevenueItem item) {
        /*
        // start included; end excluded
        if(item.getChargeTime().isBefore(this.period.getStartDateTime()))
            return false;
        if(item.getChargeTime().isBefore(this.period.getEndDateTime()))
            return true;
        return false;
        // start excluded; end included
        if(item.getChargeTime().isAfter(this.period.getEndDateTime()))
            return false;
        if(item.getChargeTime().isAfter(this.period.getStartDateTime()))
            return true;
        return false;
        */
        // start included, end included
        if(item.getChargeTime().isAfter(this.period.getEndDateTime()))
            return false;
        if(item.getChargeTime().isBefore(this.period.getStartDateTime()))
            return false;
        return true;
    }

    public Double getAmount() {
        Double out = 0d;
        for(RevenueItem i:this.revenueItems) {
            out+=i.getOverallValue();
        }
        return out;
    }

}
