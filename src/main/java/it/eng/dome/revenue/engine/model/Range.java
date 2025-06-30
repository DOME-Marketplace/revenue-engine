package it.eng.dome.revenue.engine.model;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.AssertTrue;

public class Range {

    @PositiveOrZero(message = "Il valore minimo deve essere >= 0")
    private Double min;

    @PositiveOrZero(message = "Il valore massimo deve essere >= 0")
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

    @AssertTrue(message = "Il valore massimo deve essere maggiore o uguale al minimo")
    public boolean isMaxGreaterOrEqualMin() {
        if (min == null || max == null) {
            return true; // lascio passare la validazione NotNull o PositiveOrZero
        }
        return max >= min;
    }
}