package in.copmap.dto.request;

import in.copmap.entity.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreateOperationRequest {

    @NotNull
    private Operation.OperationType type;

    @NotBlank
    @Size(max = 200)
    private String title;

    private String description;

    @NotNull
    private Instant plannedStart;

    @NotNull
    private Instant plannedEnd;

    // GeoJSON polygon string for op area
    private String areaGeoJson;

    @NotBlank
    private String locationName;

    private Operation.ShiftType shiftType;

    // Patrol-specific fields
    private PatrolConfigRequest patrolConfig;

    // Checkpoints (Naka points or patrol waypoints)
    @Valid
    private List<CheckpointRequest> checkpoints;

    @Data
    public static class PatrolConfigRequest {
        private String beatName;
        private String routeGeoJson;
        @Min(1) private Integer expectedRounds;
        @Min(5) @Max(480) private Integer roundInterval;  // minutes
    }

    @Data
    public static class CheckpointRequest {
        @NotBlank private String name;
        @NotNull @DecimalMin("-90") @DecimalMax("90") private Double latitude;
        @NotNull @DecimalMin("-180") @DecimalMax("180") private Double longitude;
        @Min(10) @Max(500) private Integer radiusMeters;
        private Integer sequenceOrder;
        private String notes;
    }
}
