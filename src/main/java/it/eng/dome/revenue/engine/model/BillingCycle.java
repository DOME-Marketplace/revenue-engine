package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;

import it.eng.dome.tmforum.tmf678.v4.model.TimePeriod;

public class BillingCycle {

    private TimePeriod billPeriod;

    private OffsetDateTime billDate;

    private OffsetDateTime paymentDueDate;

    public TimePeriod getBillPeriod() {
        return billPeriod;
    }

    public void setBillPeriod(TimePeriod billPeriod) {
        this.billPeriod = billPeriod;
    }

    public OffsetDateTime getBillDate() {
        return billDate;
    }

    public void setBillDate(OffsetDateTime billDate) {
        this.billDate = billDate;
    }

    public OffsetDateTime getPaymentDueDate() {
        return paymentDueDate;
    }

    public void setPaymentDueDate(OffsetDateTime paymentDueDate) {
        this.paymentDueDate = paymentDueDate;
    }

}
