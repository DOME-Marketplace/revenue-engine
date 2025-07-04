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
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }
    @JsonIgnore
    @AssertTrue(message = "The maximum value must be greater than or equal to the minimum.")
    public boolean isMaxGreaterOrEqualMin() {
        if (min == null || max == null) {
            return true; // lascio passare la validazione NotNull o PositiveOrZero
        }
        return max >= min;
    }
}