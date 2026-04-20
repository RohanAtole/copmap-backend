package in.copmap.dto.response;

import in.copmap.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private UUID id;
        private String badgeNumber;
        private String fullName;
        private User.UserRole role;
        private String rank;
        private String stationName;
    }
}
