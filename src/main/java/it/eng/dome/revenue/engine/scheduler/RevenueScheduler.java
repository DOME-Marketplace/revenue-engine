package it.eng.dome.revenue.engine.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.eng.dome.revenue.engine.service.TmfPeristenceService;

@Component
public class RevenueScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RevenueScheduler.class);

    private final TmfPeristenceService tmfPersistenceService;

    private boolean enabled;

    private String cronExpression;
    
    private final AtomicBoolean running = new AtomicBoolean(false);


    public RevenueScheduler(TmfPeristenceService tmfPersistenceService,
                           @Value("${scheduler.persist-revenue-bills.enabled:true}") boolean enabled,
                           @Value("${scheduler.persist-revenue-bills.cron:0 0 */1 * * *}") String cronExpression) {
        this.tmfPersistenceService = tmfPersistenceService;
        this.enabled = enabled;
        this.cronExpression = cronExpression;
        logger.info("RevenueScheduler INITIALIZED - cron: {}, enabled: {}", this.cronExpression, this.enabled);
    }

        @Scheduled(cron = "${scheduler.persist-revenue-bills.cron:0 0 */1 * * *}")
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

