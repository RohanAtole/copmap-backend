package in.copmap.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class LocationUpdateRequest {

    @NotNull @DecimalMin("-90") @DecimalMax("90")
    private Double latitude;

    @NotNull @DecimalMin("-180") @DecimalMax("180")
    private Double longitude;

    private Double accuracyMeters;
    private Double speedKmh;
    private Double headingDegrees;

    @Min(0) @Max(100)
    private Integer batteryPercent;

    // Officer reports which op they are currently on duty for
    private UUID operationId;
}
