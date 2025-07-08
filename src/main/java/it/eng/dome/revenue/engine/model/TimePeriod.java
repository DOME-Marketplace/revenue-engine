package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimePeriod implements Comparable<TimePeriod> {

	@NotNull
	private OffsetDateTime fromDate; 
    
	@NotNull
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
	
	@AssertTrue(message = "Invalid date range")
	@JsonIgnore
	public boolean isDateRangeValid() {
	    // Se una delle due date è null, considera il range non valido
	    if (fromDate == null || toDate == null) {
	        return false;
	    }
	    return !fromDate.isAfter(toDate);
	}

	@Override
	public int compareTo(TimePeriod o) {
		int fromComparison = this.fromDate.compareTo(o.fromDate);
		if (fromComparison != 0) {
			return fromComparison;
		}
		return this.toDate.compareTo(o.toDate);
	}

	public String toString() {
		return "TimePeriod [fromDate=" + fromDate + ", toDate=" + toDate + "]";
	}

}