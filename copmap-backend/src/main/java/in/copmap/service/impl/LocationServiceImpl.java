package in.copmap.service.impl;

import in.copmap.dto.request.LocationUpdateRequest;
import in.copmap.dto.response.OfficerLocationResponse;
import in.copmap.entity.LocationPing;
import in.copmap.entity.Operation;
import in.copmap.entity.User;
import in.copmap.exception.CopMapException;
import in.copmap.repository.LocationPingRepository;
import in.copmap.repository.OperationRepository;
import in.copmap.security.SecurityUtils;
import in.copmap.service.AlertService;
import in.copmap.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationServiceImpl implements LocationService {

    // Redis key patterns:
    //   officer:location:{officerId}         → latest OfficerLocationResponse (JSON)
    //   operation:officers:{operationId}     → Set of officer IDs currently on this op

    private static final String OFFICER_LOCATION_KEY = "officer:location:";
    private static final String OPERATION_OFFICERS_KEY = "operation:officers:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final LocationPingRepository locationPingRepository;
    private final OperationRepository operationRepository;
    private final AlertService alertService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.location.cache-ttl:300}")
    private long cacheTtlSeconds;

    // ── LOCATION PING ─────────────────────────────────────────────────

    @Override
    @Transactional
    public void updateLocation(LocationUpdateRequest request) {
        User officer = SecurityUtils.getCurrentUser();

        Operation operation = null;
        if (request.getOperationId() != null) {
            operation = operationRepository.findById(request.getOperationId())
                    .orElseThrow(() -> new CopMapException("OPERATION_NOT_FOUND",
                            "Operation not found: " + request.getOperationId()));
        }

        // 1. Persist to DB (append-only audit trail)
        LocationPing ping = LocationPing.builder()
                .officer(officer)
                .operation(operation)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyMeters(request.getAccuracyMeters())
                .speedKmh(request.getSpeedKmh())
                .headingDegrees(request.getHeadingDegrees())
                .batteryPercent(request.getBatteryPercent())
                .recordedAt(Instant.now())
                .build();
        locationPingRepository.save(ping);

        // 2. Update Redis cache (fast, O(1) lookup for live map)
        OfficerLocationResponse locationResponse = OfficerLocationResponse.builder()
                .officerId(officer.getId())
                .badgeNumber(officer.getBadgeNumber())
                .fullName(officer.getFullName())
                .operationId(request.getOperationId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracyMeters(request.getAccuracyMeters())
                .speedKmh(request.getSpeedKmh())
                .headingDegrees(request.getHeadingDegrees())
                .batteryPercent(request.getBatteryPercent())
                .recordedAt(ping.getRecordedAt())
                .isOnline(true)
                .build();

        String locationKey = OFFICER_LOCATION_KEY + officer.getId();
        redisTemplate.opsForValue().set(locationKey, locationResponse,
                Duration.ofSeconds(cacheTtlSeconds));

        // 3. Track which officers are on this operation
        if (request.getOperationId() != null) {
            String opKey = OPERATION_OFFICERS_KEY + request.getOperationId();
            redisTemplate.opsForSet().add(opKey, officer.getId().toString());
            redisTemplate.expire(opKey, cacheTtlSeconds * 2, TimeUnit.SECONDS);
        }

        // 4. Broadcast over WebSocket for real-time monitoring dashboard
        if (request.getOperationId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/operations/" + request.getOperationId() + "/location",
                    locationResponse);
        }

        // 5. Check for low battery alert
        if (request.getBatteryPercent() != null && request.getBatteryPercent() < 15) {
            alertService.createLowBatteryAlert(officer, operation, request.getBatteryPercent());
        }

        log.debug("Location updated for officer {} at ({}, {})",
                officer.getBadgeNumber(), request.getLatitude(), request.getLongitude());
    }

    // ── GET LIVE LOCATIONS ────────────────────────────────────────────

    @Override
    public List<OfficerLocationResponse> getLiveLocationsForOperation(UUID operationId) {
        String opKey = OPERATION_OFFICERS_KEY + operationId;
        Set<Object> officerIds = redisTemplate.opsForSet().members(opKey);

        if (officerIds == null || officerIds.isEmpty()) {
            return Collections.emptyList();
        }

        Instant staleThreshold = Instant.now().minusSeconds(120);

        return officerIds.stream()
                .map(id -> {
                    String locationKey = OFFICER_LOCATION_KEY + id;
                    Object cached = redisTemplate.opsForValue().get(locationKey);
                    if (cached instanceof OfficerLocationResponse loc) {
                        // Mark as offline if last ping is stale
                        loc.setOnline(loc.getRecordedAt().isAfter(staleThreshold));
                        return loc;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<OfficerLocationResponse> getOfficerLocation(UUID officerId) {
        String key = OFFICER_LOCATION_KEY + officerId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof OfficerLocationResponse loc) {
            Instant staleThreshold = Instant.now().minusSeconds(120);
            loc.setOnline(loc.getRecordedAt().isAfter(staleThreshold));
            return Optional.of(loc);
        }
        return Optional.empty();
    }

    // ── TRIGGER SOS ───────────────────────────────────────────────────

    @Override
    @Transactional
    public void triggerSOS(UUID operationId) {
        User officer = SecurityUtils.getCurrentUser();
        Operation operation = operationId != null
                ? operationRepository.findById(operationId).orElse(null)
                : null;

        // Get last known location from Redis
        Optional<OfficerLocationResponse> lastLoc = getOfficerLocation(officer.getId());

        alertService.createSOSAlert(officer, operation, lastLoc.orElse(null));
        log.warn("SOS triggered by officer {} on operation {}", officer.getBadgeNumber(), operationId);
    }
}
