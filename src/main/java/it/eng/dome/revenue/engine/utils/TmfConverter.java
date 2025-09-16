package it.eng.dome.revenue.engine.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TmfConverter {
	
    private static final Logger logger = LoggerFactory.getLogger(TmfConverter.class);


    public static it.eng.dome.tmforum.tmf620.v4.model.TimePeriod convertTPto620(it.eng.dome.tmforum.tmf678.v4.model.TimePeriod source) {
        if (source == null) return null;

        it.eng.dome.tmforum.tmf620.v4.model.TimePeriod target = new it.eng.dome.tmforum.tmf620.v4.model.TimePeriod();
        target.setStartDateTime(source.getStartDateTime());
        target.setEndDateTime(source.getEndDateTime());
        return target;
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
	
	public static it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef convertBillingAccountRefTo678(
			it.eng.dome.tmforum.tmf637.v4.model.BillingAccountRef in) {

		if (in == null) {
			return null;
		}

		it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef out = new it.eng.dome.tmforum.tmf678.v4.model.BillingAccountRef();
		out.setId(in.getId());
		out.setHref(in.getHref());
		out.setName(in.getName());

		return out;
	}
	
	public static List<it.eng.dome.tmforum.tmf678.v4.model.RelatedParty> convertRpTo678(List<it.eng.dome.tmforum.tmf637.v4.model.RelatedParty> list637) {
		
	    if (list637 == null) {
	        return null;
	    }
	
	    List<it.eng.dome.tmforum.tmf678.v4.model.RelatedParty> list678 = new ArrayList<>();
	
	    for (it.eng.dome.tmforum.tmf637.v4.model.RelatedParty rp637 : list637) {
	        it.eng.dome.tmforum.tmf678.v4.model.RelatedParty rp678 = new it.eng.dome.tmforum.tmf678.v4.model.RelatedParty();
	
	        rp678.setId(rp637.getId());
	        try {
				rp678.setHref(new URI(rp637.getId()));
			} catch (URISyntaxException e) {
				logger.warn("Invalid URI for RelatedParty id={} -> {}", rp637.getId(), e.getMessage());
			}
	        rp678.setName(rp637.getName());
	        rp678.setRole(rp637.getRole());
	        rp678.setAtReferredType(rp637.getAtReferredType()); // ??
	
	        list678.add(rp678);
	    }
	
	    return list678;
	}
}
