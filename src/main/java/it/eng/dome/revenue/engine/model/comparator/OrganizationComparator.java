package it.eng.dome.revenue.engine.model.comparator;

import java.util.Comparator;

import it.eng.dome.tmforum.tmf632.v4.model.Organization;

public class OrganizationComparator implements Comparator<Organization> {

    @Override
    public int compare(Organization c1, Organization c2) {
        int result = c1.getTradingName().compareTo(c2.getTradingName());
        if (result != 0)
            return result;
        result = c1.getId().compareTo(c2.getId());
        if (result != 0)
            return result;
        return Integer.valueOf(c1.hashCode()).compareTo(c2.hashCode());
    }

}