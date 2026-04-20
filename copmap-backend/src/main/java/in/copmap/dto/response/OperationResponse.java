package in.copmap.dto.response;

import in.copmap.entity.Operation;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OperationResponse {

    private UUID id;
    private Operation.OperationType type;
    private String title;
    private String description;
    private Operation.OperationStatus status;

    private Instant plannedStart;
    private Instant plannedEnd;
    private Instant actualStart;
    private Instant actualEnd;

    private String areaGeoJson;
    private String locationName;
    private String stationName;
    private Operation.ShiftType shiftType;

    private CreatedByInfo createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    // Summary counts
    private int totalAssignments;
    private int onDutyCount;
    private int pendingCount;
    private long openAlertsCount;

    private PatrolConfigInfo patrolConfig;
    private List<CheckpointInfo> checkpoints;

    @Data
    @Builder
    public static class CreatedByInfo {
        private UUID id;
        private String badgeNumber;
        private String fullName;
        private String rank;
    }

    @Data
    @Builder
    public static class PatrolConfigInfo {
        private String beatName;
        private String routeGeoJson;
        private Integer expectedRounds;
        private Integer roundInterval;
    }

    @Data
    @Builder
    public static class CheckpointInfo {
        private UUID id;
        private String name;
        private Double latitude;
        private Double longitude;
        private Integer radiusMeters;
        private Integer sequenceOrder;
        private String notes;
    }
}
