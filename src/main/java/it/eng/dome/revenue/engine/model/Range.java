package it.eng.dome.revenue.engine.model;

import jakarta.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.AssertTrue;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Range {

    @PositiveOrZero(message = "Min value must be >= 0")
    private Double min;

    @PositiveOrZero(message = "Max value must be >= 0")
    private Double max;

    public Range() {}

    public Range(Double min, Double max) {
        this.min = min;
        this.max = max;
    }

    public Double getMin() {
        return (this.min==null) ? Double.NEGATIVE_INFINITY : this.min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return (this.max==null) ? Double.POSITIVE_INFINITY : this.max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public boolean inRange(Double value) {
        if (value == null) {
            return false;
        }
        return value >= this.getMin() && value <= getMax();
    }

    @JsonIgnore
    @AssertTrue(message = "The maximum value must be greater than or equal to the minimum.")
    public boolean isMaxGreaterOrEqualMin() {
        if (min == null || max == null) {
            return true;
        }
        return max >= min;
    }
    
   
    @Override
    public String toString() {
		return "Range{" +
				"min=" + min +
				", max=" + max +
				'}';
	}
}
