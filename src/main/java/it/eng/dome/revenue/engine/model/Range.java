package it.eng.dome.revenue.engine.model;

public class Range {

    private Double min;
    
    private Double max;

    
    public Range() {}
    public Range(Double min, Double max) {
		super();
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

}
