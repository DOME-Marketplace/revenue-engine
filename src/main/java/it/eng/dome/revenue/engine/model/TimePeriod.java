package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
//import java.util.Calendar;

public class TimePeriod {

    private OffsetDateTime fromDate; 
    
    private OffsetDateTime toDate;

    public TimePeriod() {}
	public TimePeriod(OffsetDateTime fromDate, OffsetDateTime toDate) {
		this.fromDate = fromDate;
		this.toDate = toDate;
	}

	public OffsetDateTime getFromDate() {
		return fromDate;
	}

	public void setFromDate(OffsetDateTime fromDate) {
		this.fromDate = fromDate;
	}

	public OffsetDateTime getToDate() {
		return toDate;
	}

	public void setToDate(OffsetDateTime toDate) {
		this.toDate = toDate;
	}

}