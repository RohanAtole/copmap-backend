package in.copmap.service;

import in.copmap.dto.request.LoginRequest;
import in.copmap.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
}
