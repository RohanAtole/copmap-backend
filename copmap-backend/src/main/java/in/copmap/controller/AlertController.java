package in.copmap.controller;

import in.copmap.dto.response.ApiResponse;
import in.copmap.entity.Alert;
import in.copmap.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /**
     * GET /api/v1/alerts/operations/{operationId}
     */
    @GetMapping("/operations/{operationId}")
    public ResponseEntity<ApiResponse<Page<Alert>>> getForOperation(
            @PathVariable UUID operationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                alertService.getAlertsForOperation(operationId, page, size)));
    }

    /**
     * POST /api/v1/alerts/{id}/acknowledge
     */
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> acknowledge(@PathVariable UUID id) {
        alertService.acknowledgeAlert(id);
        return ResponseEntity.ok(ApiResponse.ok("Alert acknowledged", null));
    }

    /**
     * POST /api/v1/alerts/{id}/resolve
     */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resolve(@PathVariable UUID id) {
        alertService.resolveAlert(id);
        return ResponseEntity.ok(ApiResponse.ok("Alert resolved", null));
    }
}
