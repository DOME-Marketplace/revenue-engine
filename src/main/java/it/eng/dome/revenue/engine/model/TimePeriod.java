package it.eng.dome.revenue.engine.model;

import java.time.OffsetDateTime;
//import java.util.Calendar;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimePeriod {

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
	
	@AssertTrue(message = "Invalid range date")
    public boolean isDateRangeValid() {
        // Se uno dei due è null, la validazione singola @NotNull si occuperà di dare errore
        if (fromDate != null && toDate != null) {
            return !fromDate.isAfter(toDate);
        }
        return true; // se sono null, lascia passare per non duplicare errore
    }

}