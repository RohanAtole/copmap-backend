package in.copmap.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Badge number is required")
    private String badgeNumber;

    @NotBlank(message = "Password is required")
    private String password;
}
