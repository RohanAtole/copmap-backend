package in.copmap.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AssignOfficersRequest {

    @NotEmpty(message = "At least one officer must be assigned")
    private List<OfficerAssignment> officers;

    @Data
    public static class OfficerAssignment {
        @NotNull
        private UUID officerId;

        // Optional: direct officer to a specific checkpoint
        private UUID checkpointId;

        private String dutyRole;  // e.g., "Beat Officer", "Naka Head", "Traffic Control"
    }
}
