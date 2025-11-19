package it.eng.dome.revenue.engine.utils;

import it.eng.dome.tmforum.tmf620.v4.model.ProductOffering;
import it.eng.dome.tmforum.tmf620.v4.model.ProductOfferingTerm;

public class ProductOfferingUtils {

    public static Boolean isDomeManagingProcurement(ProductOffering productOffering) {
        String procurementMode = getProcurementMode(productOffering);
        if("automatic".equals(procurementMode))
            return true;
        return false;
        
    }

    public static Boolean isDomeManagingBilling(ProductOffering productOffering) {
        String procurementMode = getProcurementMode(productOffering);
        if("payment-automatic".equals(procurementMode) || "automatic".equals(procurementMode))
            return true;
        return false;
    }

    public static Boolean isDomeManagingPayment(ProductOffering productOffering) {
        String procurementMode = getProcurementMode(productOffering);
        if("payment-automatic".equals(procurementMode) || "automatic".equals(procurementMode))
            return true;
        return false;
    }

    private static String getProcurementMode(ProductOffering productOffering) {
        if(productOffering!=null) {
            for(ProductOfferingTerm term: productOffering.getProductOfferingTerm()) {
                if(term!=null) {
                    if("procurement".equalsIgnoreCase(term.getName())) {
                        String description = term.getDescription();
                        if(description!=null) {
                            return description.trim().toLowerCase();
                        }
                        break;
                    }
                }
            }
        }
        return null;
    }


}
