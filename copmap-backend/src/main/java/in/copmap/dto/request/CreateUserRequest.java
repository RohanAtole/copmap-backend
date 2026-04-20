package in.copmap.dto.request;

import in.copmap.entity.User;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank
    @Size(max = 20)
    private String badgeNumber;

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Must be a valid 10-digit Indian mobile number")
    private String phone;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull
    private User.UserRole role;

    private String rank;

    @NotBlank
    private String stationName;
}
