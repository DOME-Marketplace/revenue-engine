package it.eng.dome.revenue.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReferencePeriod {

    @JsonValue
    String value;
    
    public ReferencePeriod() {
    	
    }

    public ReferencePeriod(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

//    CURRENT_BILLING_PERIOD,
//    PREVIOUS_BILLING_PERIOD,
//    CURRENT_SUBSCRIPTION_PERIOD,
//    PREVIOUS_SUBSCRIPTION_PERIOD
}