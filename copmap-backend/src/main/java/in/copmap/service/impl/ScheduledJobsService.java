package in.copmap.service.impl;

import in.copmap.entity.*;
import in.copmap.repository.*;
import in.copmap.service.AlertService;
import in.copmap.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Scheduled jobs running in the background.
 *
 * 1. Offline Detection   — every 60s: find officers with stale pings for active ops → alert
 * 2. Overdue Operations  — every 5m:  find ACTIVE ops past plannedEnd → flag/alert
 * 3. Auto-Start          — every 2m:  PUBLISHED ops whose plannedStart has passed → nudge
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobsService {

    private static final String OFFICER_LOCATION_KEY = "officer:location:";
    private static final String OPERATION_OFFICERS_KEY = "operation:officers:";

    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final AlertService alertService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AlertRepository alertRepository;

    @Value("${app.location.stale-threshold:120}")
    private long staleThresholdSeconds;

    /**
     * Every 60 seconds: detect offline officers on active operations.
     * An officer is "offline" if their Redis location key has expired
     * (TTL expired = no ping for > cacheTtlSeconds).
     */
    @Scheduled(fixedDelay = 60_000)
    public void detectOfflineOfficers() {
        List<Operation> activeOps = operationRepository.findByStatusIn(
                List.of(Operation.OperationStatus.ACTIVE));

        for (Operation op : activeOps) {
            String opKey = OPERATION_OFFICERS_KEY + op.getId();
            Set<Object> officerIds = redisTemplate.opsForSet().members(opKey);
            if (officerIds == null) continue;

            for (Object oidObj : officerIds) {
                String locationKey = OFFICER_LOCATION_KEY + oidObj;
                Boolean exists = redisTemplate.hasKey(locationKey);

                if (Boolean.FALSE.equals(exists)) {
                    // No key = TTL expired = officer went offline
                    userRepository.findById(java.util.UUID.fromString(oidObj.toString()))
                            .ifPresent(officer -> {
                                // Avoid duplicate alerts: check if an OPEN OFFICER_OFFLINE alert exists
                                boolean alreadyAlerted = alertRepository
                                        .findByStatusAndSeverityIn(Alert.AlertStatus.OPEN,
                                                List.of(Alert.AlertSeverity.HIGH, Alert.AlertSeverity.CRITICAL))
                                        .stream()
                                        .anyMatch(a -> a.getOfficer() != null
                                                && a.getOfficer().getId().equals(officer.getId())
                                                && a.getAlertType() == Alert.AlertType.OFFICER_OFFLINE);

                                if (!alreadyAlerted) {
                                    log.warn("Officer {} appears offline for op {}", officer.getBadgeNumber(), op.getId());
                                    alertService.createOfficerOfflineAlert(officer, op.getId());
                                }
                            });
                }
            }
        }
    }

    /**
     * Every 5 minutes: flag operations that are past planned end time but still ACTIVE.
     */
    @Scheduled(fixedDelay = 300_000)
    public void flagOverdueOperations() {
        Instant threshold = Instant.now().minusSeconds(1800); // 30 min grace
        List<Operation> overdue = operationRepository.findOverdueActiveOperations(Operation.OperationStatus.ACTIVE, threshold);

        for (Operation op : overdue) {
            log.warn("Operation {} is overdue (planned end: {})", op.getId(), op.getPlannedEnd());
            // Could create an OPERATION_DELAYED alert or notify station officer
        }
    }
}
