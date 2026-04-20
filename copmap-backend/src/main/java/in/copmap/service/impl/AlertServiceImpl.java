package in.copmap.service.impl;

import in.copmap.dto.response.OfficerLocationResponse;
import in.copmap.entity.Alert;
import in.copmap.entity.Operation;
import in.copmap.entity.User;
import in.copmap.exception.CopMapException;
import in.copmap.repository.AlertRepository;
import in.copmap.repository.OperationRepository;
import in.copmap.repository.UserRepository;
import in.copmap.security.SecurityUtils;
import in.copmap.service.AlertService;
import in.copmap.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void createLowBatteryAlert(User officer, Operation operation, int batteryPercent) {
        Alert alert = Alert.builder()
                .officer(officer)
                .operation(operation)
                .alertType(Alert.AlertType.CUSTOM)
                .severity(batteryPercent < 5 ? Alert.AlertSeverity.HIGH : Alert.AlertSeverity.MEDIUM)
                .title("Low Battery: " + officer.getFullName())
                .message(String.format("Officer %s battery at %d%%", officer.getBadgeNumber(), batteryPercent))
                .metadata(Map.of("batteryPercent", batteryPercent))
                .build();

        saveAndBroadcast(alert);
    }

    @Override
    @Transactional
    public void createSOSAlert(User officer, Operation operation, OfficerLocationResponse lastLocation) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("officerBadge", officer.getBadgeNumber());
        if (lastLocation != null) {
            metadata.put("latitude", lastLocation.getLatitude());
            metadata.put("longitude", lastLocation.getLongitude());
            metadata.put("mapsLink",
                    "https://maps.google.com/?q=" + lastLocation.getLatitude()
                    + "," + lastLocation.getLongitude());
        }

        Alert alert = Alert.builder()
                .officer(officer)
                .operation(operation)
                .alertType(Alert.AlertType.SOS)
                .severity(Alert.AlertSeverity.CRITICAL)
                .title("🚨 SOS from " + officer.getFullName())
                .message("Officer " + officer.getBadgeNumber() + " has triggered an emergency SOS!")
                .metadata(metadata)
                .build();

        Alert saved = saveAndBroadcast(alert);

        // Notify station officer immediately
        notificationService.broadcastSOSNotification(saved);
        log.error("SOS ALERT CREATED: Officer {} at op {}", officer.getBadgeNumber(),
                operation != null ? operation.getId() : "unknown");
    }

    @Override
    @Transactional
    public void createOfficerOfflineAlert(User officer, UUID operationId) {
        Operation operation = operationId != null
                ? operationRepository.findById(operationId).orElse(null)
                : null;

        Alert alert = Alert.builder()
                .officer(officer)
                .operation(operation)
                .alertType(Alert.AlertType.OFFICER_OFFLINE)
                .severity(Alert.AlertSeverity.HIGH)
                .title("Officer Offline: " + officer.getFullName())
                .message("No location ping received from " + officer.getBadgeNumber()
                        + " for over 2 minutes")
                .build();

        saveAndBroadcast(alert);
    }

    @Override
    @Transactional
    public void acknowledgeAlert(UUID alertId) {
        User actor = SecurityUtils.getCurrentUser();
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new CopMapException("ALERT_NOT_FOUND", "Alert not found: " + alertId));

        alert.setStatus(Alert.AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(actor);
        alert.setAcknowledgedAt(Instant.now());
        alertRepository.save(alert);
    }

    @Override
    @Transactional
    public void resolveAlert(UUID alertId) {
        User actor = SecurityUtils.getCurrentUser();
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new CopMapException("ALERT_NOT_FOUND", "Alert not found: " + alertId));

        alert.setStatus(Alert.AlertStatus.RESOLVED);
        alert.setResolvedBy(actor);
        alert.setResolvedAt(Instant.now());
        alertRepository.save(alert);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Alert> getAlertsForOperation(UUID operationId, int page, int size) {
        return alertRepository.findByOperationId(operationId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    private Alert saveAndBroadcast(Alert alert) {
        Alert saved = alertRepository.save(alert);

        // Push to monitoring dashboard WebSocket topic
        String destination = alert.getOperation() != null
                ? "/topic/operations/" + alert.getOperation().getId() + "/alerts"
                : "/topic/alerts/global";

        messagingTemplate.convertAndSend(destination, Map.of(
                "id", saved.getId(),
                "type", saved.getAlertType(),
                "severity", saved.getSeverity(),
                "title", saved.getTitle(),
                "message", saved.getMessage(),
                "createdAt", saved.getCreatedAt()
        ));

        return saved;
    }
}
