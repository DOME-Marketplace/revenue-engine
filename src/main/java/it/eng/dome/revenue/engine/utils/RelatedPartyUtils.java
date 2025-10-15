package it.eng.dome.revenue.engine.utils;

import java.util.ArrayList;
import java.util.List;

import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

class RP {

    String id;
    String role;

    public RP(String id, String role) {
        this.id = id;
        this.role = role;
    }

}

public class RelatedPartyUtils {

    // work on a common class
    private static Boolean hasRPWithRole(List<RP> relatedParties, String partyId, Role partyRole) {
        if (relatedParties != null) {
            for (RP rp : relatedParties) {
                if (partyId != null && partyId.equalsIgnoreCase(rp.id) && partyRole != null
                        && partyRole.getValue().equalsIgnoreCase(rp.role)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Boolean productHasPartyWithRole(Product product, String partyId, Role partyRole) {
        List<RP> parties = product.getRelatedParty().stream().map(party -> new RP(party.getId(), party.getRole()))
                .toList();
        return RelatedPartyUtils.hasRPWithRole(parties, partyId, partyRole);
    }

    public static Boolean customerBillHasPartyWithRole(CustomerBill bill, String partyId, Role partyRole) {
        List<RP> parties = bill.getRelatedParty().stream().map(party -> new RP(party.getId(), party.getRole()))
                .toList();
        return RelatedPartyUtils.hasRPWithRole(parties, partyId, partyRole);
    }

    public static Boolean subscriptionHasPartyWithRole(Subscription subscription, String partyId, Role partyRole) {
        List<RP> parties = subscription.getRelatedParties().stream()
                .map(party -> new RP(party.getId(), party.getRole())).toList();
        return RelatedPartyUtils.hasRPWithRole(parties, partyId, partyRole);
    }

    public static List<Product> retainProductsWithParty(List<Product> products, String partyId, Role partyRole) {
        List<Product> retainedProducts = new ArrayList<>();
        for (Product p : products)
            if (RelatedPartyUtils.productHasPartyWithRole(p, partyId, partyRole))
                retainedProducts.add((p));
        return retainedProducts;
    }

    public static List<CustomerBill> retainCustomerBillsWithParty(List<CustomerBill> customerBills, String partyId,
            Role partyRole) {
        List<CustomerBill> retainedBills = new ArrayList<>();
        for (CustomerBill b : customerBills)
            if (RelatedPartyUtils.customerBillHasPartyWithRole(b, partyId, partyRole))
                retainedBills.add((b));
        return retainedBills;
    }

    public static List<Subscription> retainSubscriptionsWithParty(List<Subscription> subscriptions, String partyId,
            Role partyRole) {
        List<Subscription> retainedSubscriptions = new ArrayList<>();
        for (Subscription s : subscriptions)
            if (RelatedPartyUtils.subscriptionHasPartyWithRole(s, partyId, partyRole))
                retainedSubscriptions.add((s));
        return retainedSubscriptions;
    }

}
