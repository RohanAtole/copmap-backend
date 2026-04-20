package in.copmap.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CloseOperationRequest {

    @NotBlank(message = "Closure notes are required")
    private String closureNotes;
}
