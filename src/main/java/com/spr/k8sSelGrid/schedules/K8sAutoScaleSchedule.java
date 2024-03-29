package com.spr.k8sSelGrid.schedules;

import com.spr.k8sSelGrid.service.GridConsoleService;
import com.spr.k8sSelGrid.service.PodScalingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class K8sAutoScaleSchedule {
    private static final Logger logger = LoggerFactory.getLogger(K8sAutoScaleSchedule.class);
    @Autowired
    private GridConsoleService service;

    @Autowired
    private PodScalingService podScalingService;

    @Scheduled(fixedDelayString = "${grid_scale_check_frequency_in_sec:10}000", initialDelay = 30000)
    public synchronized void checkAndAutoScaleChrome() {
        try {
            podScalingService.adjustScale("chrome", service.getStatusforGrid4(), service.getGrid4NodesInfo());
        } catch (Exception e) {
            logger.error("Error in running checkAndAutoScale scheduler for {}: {}", "chrome", e);
        }
    }

    @Scheduled(fixedDelayString = "${grid_scale_check_frequency_in_sec:10}000", initialDelay = 30000)
    public synchronized void checkAndAutoScaleFirefox() {
        try {
            podScalingService.adjustScale("firefox", service.getStatusforGrid4(), service.getGrid4NodesInfo());
        } catch (Exception e) {
            logger.error("Error in running checkAndAutoScale scheduler for {}: {}", "firefox", e);
        }
    }

    @Scheduled(cron = "${grid_daily_cleanup_cron}")
    public synchronized void restartAllNodes() {
        try {
            logger.info("This daily cleanup is not active as of now ....");
            // podScalingService.cleanUp();
        } catch (Exception e) {
            logger.error("Error in running restartAllNodes scheduler: {}", e);
        }
    }
}
