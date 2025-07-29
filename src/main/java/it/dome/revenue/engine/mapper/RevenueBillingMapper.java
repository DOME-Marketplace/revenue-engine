package it.dome.revenue.engine.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import it.eng.dome.revenue.engine.model.RevenueItem;
import it.eng.dome.revenue.engine.model.RevenueStatement;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedBillingRateCharacteristic;
import it.eng.dome.tmforum.tmf678.v4.model.AppliedCustomerBillingRate;
import it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef;
import it.eng.dome.tmforum.tmf678.v4.model.Money;
import it.eng.dome.tmforum.tmf678.v4.model.ProductRef;

public class RevenueBillingMapper {

    public static AppliedCustomerBillingRate toACBR(RevenueStatement rs, BillingAccountRef billingAccountRef) {
        if (rs == null) return null;

        //FIXME: assume get 0
        RevenueItem rootItem = rs.getRevenueItems().get(0);

        AppliedCustomerBillingRate acbr = new AppliedCustomerBillingRate();
        acbr.setId(UUID.randomUUID().toString());
        acbr.setHref(acbr.getId()); // check if it's ok
        acbr.setName("Applied Customer Billing Rate of " + rootItem.getName());
        acbr.setDescription("Applied Customer Billing Rate of " + rs.getDescription() + "for period " + rs.getPeriod().getStartDateTime() + "-" + rs.getPeriod().getEndDateTime());
        acbr.setDate(rs.getSubscription().getStartDate());
        acbr.setIsBilled(false);
        acbr.setType(null); //TODO: discuss
        acbr.setPeriodCoverage(rs.getPeriod());
        acbr.setBill(null); // beacuse bill will be filled when the party pays the invoice 
        
        acbr.setProduct(toProductRef(rs.getSubscription()));
        acbr.setBillingAccount(billingAccountRef);
        acbr.setRelatedParty(rs.getSubscription().getRelatedParties());
        //TODO: add appliedTax
        
        // Set taxExcludedAmount (overall value)
        if (rootItem.getOverallValue() != null) {
            Money money = new Money();
            money.setValue(rootItem.getOverallValue().floatValue());
            money.setUnit(rootItem.getCurrency());
            acbr.setTaxExcludedAmount(money);
        }
        
        //TODO: add taxIncludedAmount

        // Set characteristics (ricorsivo)
        if (rootItem.getItems() != null) {
            List<AppliedBillingRateCharacteristic> characteristics = new ArrayList<>();
            for (RevenueItem item : rootItem.getItems()) {
                collectCharacteristics(item, characteristics);
            }
            acbr.setCharacteristic(characteristics);
        }

        return acbr;
    }
    
    public static ProductRef toProductRef(Subscription subscription) {
        if (subscription == null) return null;

        ProductRef ref = new ProductRef();
        ref.setId(subscription.getId());
        ref.setHref(subscription.getId()); //TODO: change this with href when sub will contains href
        ref.setName(subscription.getName());
      
        return ref;
    }

    
    private static void collectCharacteristics(RevenueItem item, List<AppliedBillingRateCharacteristic> collector) {
        if (item.getValue() != null) {
            AppliedBillingRateCharacteristic c = new AppliedBillingRateCharacteristic();
            c.setName(item.getName());
            c.setValue(item.getValue());
            c.setValueType("number"); // o "decimal"
            collector.add(c);
        }

        if (item.getItems() != null) {
            for (RevenueItem child : item.getItems()) {
                collectCharacteristics(child, collector);
            }
        }
    }


    // TODO: Revert Mapping
}

