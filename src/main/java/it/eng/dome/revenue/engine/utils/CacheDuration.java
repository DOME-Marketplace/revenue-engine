package it.eng.dome.revenue.engine.utils;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "caching")
public class CacheDuration {

    private ServiceCache tmf;
    private ServiceCache revenue;
    private HealthCache health;

    public ServiceCache getTmf() { return tmf; }
    public void setTmf(ServiceCache tmf) { this.tmf = tmf; }

    public ServiceCache getRevenue() { return revenue; }
    public void setRevenue(ServiceCache revenue) { this.revenue = revenue; }

    public HealthCache getHealth() { return health; }
    public void setHealth(HealthCache health) { this.health = health; }

    public static class ServiceCache {
        private boolean enabled;
        private Map<String, Duration> duration;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Map<String, Duration> getDuration() { return duration; }
        public void setDuration(Map<String, Duration> duration) { this.duration = duration; }

        public Duration get(String key) {
            Duration value = duration.get(key);
            if (value == null) {
                throw new IllegalStateException("Cache key '" + key + "' not found in YAML");
            }
            return value;
        }
    }

    public static class HealthCache {
        private boolean enabled;
        private Duration duration;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Duration getDuration() { return duration; }
        public void setDuration(Duration duration) { this.duration = duration; }
    }
}
