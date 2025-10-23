package it.eng.dome.revenue.engine.utils;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "cache")
public class CacheDuration {
	
	private Map<String, Duration> duration;

    public Map<String, Duration> getDuration() {
        return duration;
    }

    public void setDuration(Map<String, Duration> duration) {
        this.duration = duration;
    }

    public Duration get(String serviceName) {
        return duration.get(serviceName);
    }

}
