package in.copmap.controller;

import in.copmap.dto.request.LocationUpdateRequest;
import in.copmap.dto.response.ApiResponse;
import in.copmap.dto.response.OfficerLocationResponse;
import in.copmap.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * POST /api/v1/location/ping
     * Mobile app sends officer GPS coordinates.
     * Persists to DB + Redis + broadcasts via WebSocket.
     * Expected frequency: every 30–60 seconds.
     */
    @PostMapping("/ping")
    public ResponseEntity<ApiResponse<Void>> ping(
            @Valid @RequestBody LocationUpdateRequest request) {
        locationService.updateLocation(request);
        return ResponseEntity.ok(ApiResponse.ok("Location updated", null));
    }

    /**
     * GET /api/v1/location/operations/{operationId}/live
     * Monitoring dashboard: get live positions of all officers on an operation.
     * Data comes from Redis (sub-millisecond response).
     */
    @GetMapping("/operations/{operationId}/live")
    public ResponseEntity<ApiResponse<List<OfficerLocationResponse>>> getLive(
            @PathVariable UUID operationId) {
        List<OfficerLocationResponse> locations =
                locationService.getLiveLocationsForOperation(operationId);
        return ResponseEntity.ok(ApiResponse.ok(locations));
    }

    /**
     * GET /api/v1/location/officers/{officerId}
     * Get single officer's last known location.
     */
    @GetMapping("/officers/{officerId}")
    public ResponseEntity<ApiResponse<OfficerLocationResponse>> getOfficerLocation(
            @PathVariable UUID officerId) {
        Optional<OfficerLocationResponse> location = locationService.getOfficerLocation(officerId);
        return location
                .map(loc -> ResponseEntity.ok(ApiResponse.ok(loc)))
                .orElse(ResponseEntity.ok(ApiResponse.fail("Officer location not found")));
    }

    /**
     * POST /api/v1/location/sos?operationId={operationId}
     * Officer triggers emergency SOS — creates CRITICAL alert with last known location.
     */
    @PostMapping("/sos")
    public ResponseEntity<ApiResponse<Void>> triggerSOS(
            @RequestParam(required = false) UUID operationId) {
        locationService.triggerSOS(operationId);
        return ResponseEntity.ok(ApiResponse.ok("SOS triggered", null));
    }
}
