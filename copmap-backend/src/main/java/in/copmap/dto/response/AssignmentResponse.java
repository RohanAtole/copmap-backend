package in.copmap.dto.response;

import in.copmap.entity.Assignment;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AssignmentResponse {
    private UUID id;
    private UUID operationId;
    private String operationTitle;
    private OfficerInfo officer;
    private Assignment.AssignmentStatus status;
    private String dutyRole;
    private Instant dutyStart;
    private Instant dutyEnd;
    private Instant acknowledgedAt;
    private Instant checkedInAt;
    private Instant checkedOutAt;
    private CheckpointInfo checkpoint;

    @Data @Builder
    public static class OfficerInfo {
        private UUID id;
        private String badgeNumber;
        private String fullName;
        private String rank;
    }

    @Data @Builder
    public static class CheckpointInfo {
        private UUID id;
        private String name;
    }
}
