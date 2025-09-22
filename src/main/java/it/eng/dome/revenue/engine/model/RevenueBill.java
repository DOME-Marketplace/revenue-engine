package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;
import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class RevenueBill {

    private TimePeriod period;
    private String subscriptionId;

    private List<RevenueItem> revenueItems;

    @JsonProperty("relatedParty") 
    private List<RelatedParty> relatedParties; 

    public RevenueBill() {
        this.revenueItems = new ArrayList<>();
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
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

    public List<RelatedParty> getRelatedParties() {
        return relatedParties;
    }

    public void setRelatedParties(List<RelatedParty> relatedParties) {
        this.relatedParties = relatedParties;
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

    public OffsetDateTime getBillTime() {
        OffsetDateTime endDateTime = this.period.getEndDateTime();
        if(endDateTime!=null)
        	return endDateTime.plusDays(3);
        return null;
    }

    private String getRelatedPartyIdWithRole(String role) {
        if(this.getRelatedParties()==null || role==null)
            return null;
        for(RelatedParty rp: this.getRelatedParties()) {
            if(role.equalsIgnoreCase(rp.getRole()))
                return rp.getId();
        }
        return null;
    }

    private String getDescriptions() {
        if(this.getRevenueItems()==null)
            return null;
        String descriptions = "";
        for(RevenueItem item: this.getRevenueItems()) {
            descriptions += item.getName();
        }
        return descriptions;
    }

    public String getId() {
        return this.generateId();
    }
    
    private String generateId() {
        // FIXME: temporary... until we have proper persistence
        String key = "";
        OffsetDateTime startDateTime = this.period.getStartDateTime();
        if(startDateTime!=null)
        	key += startDateTime.toString();
        OffsetDateTime endDateTime = this.period.getEndDateTime();
        if(endDateTime!=null)
        	key += endDateTime.toString();
        key += this.getBillTime().toString();
        key += this.getRelatedPartyIdWithRole("buyer");
        key += this.getRelatedPartyIdWithRole("seller");
        key += this.getAmount().toString();
        key += this.getDescriptions();
        // include the subscription id (buyer and plan) + the bill nr
        return "urn:ngsi-ld:revenuebill:" + this.subscriptionId.substring(20) + "-" + UUID.nameUUIDFromBytes(key.getBytes()).toString();
    }

}
