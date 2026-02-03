package it.eng.dome.revenue.engine.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.eng.dome.revenue.engine.service.TmfPersistenceService;

@Component
public class RevenueScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RevenueScheduler.class);

    private final TmfPersistenceService tmfPersistenceService;

    @Value("${persistence.scheduler.enabled:true}")
    private boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public RevenueScheduler(TmfPersistenceService tmfPersistenceService) {
        this.tmfPersistenceService = tmfPersistenceService;
    }

    @Scheduled(cron = "${persistence.scheduler.cron:0 0 */1 * * *}")
    public void persistAllRevenueBills() {
        if (!enabled) {
            logger.debug("RevenueScheduler is disabled");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            logger.warn("Previous execution still in progress, skipping this run");
            return;
        }

        try {
            logger.info("RevenueScheduler execution started");
            tmfPersistenceService.persistAllRevenueBills();
            logger.info("RevenueScheduler execution completed");
        } catch (Exception e) {
            logger.error("Error during RevenueScheduler execution", e);
        } finally {
            running.set(false);
        }
    }

    public boolean isSchedulerRunning() {
        return running.get();
    }
}


