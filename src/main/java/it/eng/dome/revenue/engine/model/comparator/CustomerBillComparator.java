package it.eng.dome.revenue.engine.model.comparator;

import java.util.Comparator;

import it.eng.dome.tmforum.tmf678.v4.model.CustomerBill;

public class CustomerBillComparator implements Comparator<CustomerBill> {

    @Override
    public int compare(CustomerBill c1, CustomerBill c2) {
        int result = c1.getBillDate().compareTo(c2.getBillDate());
        if (result != 0)
            return result;
        return Integer.valueOf(c1.hashCode()).compareTo(c2.hashCode());
    }

}