package in.copmap.service;

import in.copmap.dto.request.LocationUpdateRequest;
import in.copmap.dto.response.OfficerLocationResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationService {
    void updateLocation(LocationUpdateRequest request);
    List<OfficerLocationResponse> getLiveLocationsForOperation(UUID operationId);
    Optional<OfficerLocationResponse> getOfficerLocation(UUID officerId);
    void triggerSOS(UUID operationId);
}
