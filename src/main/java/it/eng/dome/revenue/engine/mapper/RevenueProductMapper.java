package it.eng.dome.revenue.engine.mapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.eng.dome.revenue.engine.model.Subscription;
import it.eng.dome.tmforum.tmf637.v4.model.Product;
import it.eng.dome.tmforum.tmf637.v4.model.ProductStatusType;

public class RevenueProductMapper {
	
	
	private static final Logger logger = LoggerFactory.getLogger(RevenueProductMapper.class);
	
	public static Product toProduct(Subscription subscription, it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef billingAccountRef) {
		
		logger.debug("Converting Subscription to Product: {}", subscription.getId());
		
		Product product = new Product();
		
		product.setId(subscription.getId());
		product.setName(subscription.getName());
		product.setDescription("Product for " + subscription.getName());
		product.setHref(subscription.getId());
		product.setIsBundle(false); // ??
		product.isCustomerVisible(false); // ??
		product.orderDate(OffsetDateTime.now()); // ??
		product.startDate(subscription.getStartDate()); // ??
		product.terminationDate(subscription.getStartDate().plusYears(1)); // ??
		product.setStatus(ProductStatusType.ACTIVE); // ??
		product.setProductSerialNumber(subscription.getId()); //??
		
		// reference to the product
		
		product.setRelatedParty(convertRpTo637(subscription.getRelatedParties()));
		
		product.setBillingAccount(convertBillingAccountRefTo637(billingAccountRef));
		
		// productCharacteristics ??
		
		// productOffering ??
		
		// productPrice ?? (is the same of the (plan? ProductOffering?))
		
		return product;
	}

    public static List<it.eng.dome.tmforum.tmf637.v4.model.RelatedParty> convertRpTo637(
            List<it.eng.dome.tmforum.tmf678.v4.model.RelatedParty> list678) {

        if (list678 == null) {
            return null;
        }

        List<it.eng.dome.tmforum.tmf637.v4.model.RelatedParty> list637 = new ArrayList<>();

        for (it.eng.dome.tmforum.tmf678.v4.model.RelatedParty rp678 : list678) {
            it.eng.dome.tmforum.tmf637.v4.model.RelatedParty rp637 = new it.eng.dome.tmforum.tmf637.v4.model.RelatedParty();

            rp637.setId(rp678.getId());
            rp637.setHref(rp678.getId());
            rp637.setName(rp678.getName());
            rp637.setRole(rp678.getRole());
            rp637.setAtReferredType(rp678.getAtReferredType()); // ??

            list637.add(rp637);
        }

        return list637;
    }

    public static it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef convertBillingAccountRefTo637(
			it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef billingAccountRef678) {

		if (billingAccountRef678 == null) {
			return null;
		}

		it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef billingAccountRef637 = new it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef();
		billingAccountRef637.setId(billingAccountRef678.getId());
		billingAccountRef637.setHref(billingAccountRef678.getHref());
		billingAccountRef637.setName(billingAccountRef678.getName());

		return billingAccountRef637;
	}
}
