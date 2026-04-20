package in.copmap.service;

import in.copmap.dto.response.OfficerLocationResponse;
import in.copmap.entity.Alert;
import in.copmap.entity.Operation;
import in.copmap.entity.User;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface AlertService {
    void createLowBatteryAlert(User officer, Operation operation, int batteryPercent);
    void createSOSAlert(User officer, Operation operation, OfficerLocationResponse lastLocation);
    void createOfficerOfflineAlert(User officer, UUID operationId);
    void acknowledgeAlert(UUID alertId);
    void resolveAlert(UUID alertId);
    Page<Alert> getAlertsForOperation(UUID operationId, int page, int size);
}
