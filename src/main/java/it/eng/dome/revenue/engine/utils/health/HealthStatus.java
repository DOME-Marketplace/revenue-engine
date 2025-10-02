package it.eng.dome.revenue.engine.utils.health;

public enum HealthStatus {

    PASS(0),
    WARN(1),
    FAIL(2);

    private int severity;

    HealthStatus(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return this.severity;
    }

}
