package it.eng.dome.revenue.engine.model;

public enum BundleOperator {

    ALTERNATIVE_HIGHER,  // choose the option with higher value
    ALTERNATIVE_LOWER,  // choose the option with lower value
    CUMULATIVE;		   // sum the value of options
}