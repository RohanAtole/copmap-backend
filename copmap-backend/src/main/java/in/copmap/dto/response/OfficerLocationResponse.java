package in.copmap.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Live officer location — broadcast over WebSocket and consumed by the monitoring dashboard.
 * Also cached in Redis with a TTL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficerLocationResponse {

    private UUID officerId;
    private String badgeNumber;
    private String fullName;
    private UUID operationId;

    private Double latitude;
    private Double longitude;
    private Double accuracyMeters;
    private Double speedKmh;
    private Double headingDegrees;
    private Integer batteryPercent;

    private Instant recordedAt;
    private boolean isOnline;  // true if last ping was within stale-threshold
}
