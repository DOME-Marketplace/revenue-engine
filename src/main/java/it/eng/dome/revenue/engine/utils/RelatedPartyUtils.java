package it.eng.dome.revenue.engine.utils;

import java.util.ArrayList;
import java.util.List;

import it.eng.dome.revenue.engine.model.Role;
import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;
import it.eng.dome.tmforum.tmf678.v4.model.RelatedParty;

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

    public static String partyIdWithRole(CustomerBill bill, Role partyRole) {
        List<RelatedParty> relatedParties = bill.getRelatedParty();
        if (relatedParties != null) {
            for (RelatedParty rp : relatedParties) {
                if (partyRole.getValue().equalsIgnoreCase(rp.getRole())) {
                    return rp.getId();
                }
            }
        }
        return null;
    }

    public static String partyIdWithRole(ProductOffering offering, Role partyRole) {
        List<it.eng.dome.tmforum.tmf620.v4.model.RelatedParty> relatedParties = offering.getRelatedParty();
        if (relatedParties != null) {
            for (it.eng.dome.tmforum.tmf620.v4.model.RelatedParty rp : relatedParties) {
                if (partyRole.getValue().equalsIgnoreCase(rp.getRole())) {
                    return rp.getId();
                }
            }
        }
        return null;
    }

    public static String partyIdWithRole(Product product, Role partyRole) {
        List<it.eng.dome.tmforum.tmf637.v4.model.RelatedParty> relatedParties = product.getRelatedParty();
        if (relatedParties != null) {
            for (it.eng.dome.tmforum.tmf637.v4.model.RelatedParty rp : relatedParties) {
                if (partyRole.getValue().equalsIgnoreCase(rp.getRole())) {
                    return rp.getId();
                }
            }
        }
        return null;
    }

    public static Boolean subscriptionHasPartyWithRole(Subscription subscription, String partyId, Role partyRole) {
        List<RP> parties = subscription.getRelatedParties().stream()
                .map(party -> new RP(party.getId(), party.getRole())).toList();
        return RelatedPartyUtils.hasRPWithRole(parties, partyId, partyRole);
    }

    public static Boolean offeringHasPartyWithRole(ProductOffering productOffering, String partyId, Role partyRole) {
        List<RP> parties = productOffering.getRelatedParty().stream()
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
            Role partyRole, boolean onlyActiveSub) {
        List<Subscription> retainedSubscriptions = new ArrayList<>();
        for (Subscription s : subscriptions) {
            // skip if subscription does NOT have that party/role
            if (!RelatedPartyUtils.subscriptionHasPartyWithRole(s, partyId, partyRole)) {
                continue;
            }
            // skip if onlyActive required and subscription is not Active
            if (onlyActiveSub && !"active".equalsIgnoreCase(s.getStatus())) {
                continue;
            }
            retainedSubscriptions.add(s);
        }
        return retainedSubscriptions;
    }

    public static List<ProductOffering> retainProductOfferingsWithParty(List<ProductOffering> offerings, String partyId, Role partyRole) {
        List<ProductOffering> retainedOfferings = new ArrayList<>();
        for (ProductOffering o : offerings)
            if (RelatedPartyUtils.offeringHasPartyWithRole(o, partyId, partyRole))
                retainedOfferings.add((o));
        return retainedOfferings;
    }

}
