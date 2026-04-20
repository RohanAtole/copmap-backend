package in.copmap.service.impl;

import in.copmap.dto.request.LoginRequest;
import in.copmap.dto.response.AuthResponse;
import in.copmap.entity.RefreshToken;
import in.copmap.entity.User;
import in.copmap.exception.CopMapException;
import in.copmap.repository.RefreshTokenRepository;
import in.copmap.repository.UserRepository;
import in.copmap.security.JwtTokenProvider;
import in.copmap.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${spring.security.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${spring.security.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getBadgeNumber(), request.getPassword()));

        User user = (User) auth.getPrincipal();

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateToken(
                Map.of("role", user.getRole().name(), "station", user.getStationName()),
                user);

        String refreshTokenValue = UUID.randomUUID().toString();
        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build());

        log.info("Officer {} logged in from station {}", user.getBadgeNumber(), user.getStationName());

        return buildAuthResponse(user, accessToken, refreshTokenValue);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new CopMapException("INVALID_REFRESH_TOKEN", "Refresh token not found"));

        if (!refreshToken.isValid()) {
            throw new CopMapException("EXPIRED_REFRESH_TOKEN", "Refresh token is expired or revoked");
        }

        User user = refreshToken.getUser();

        // Rotate refresh token (one-time use)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String newRefreshToken = UUID.randomUUID().toString();
        refreshTokenRepository.save(RefreshToken.builder()
                .token(newRefreshToken)
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .build());

        String accessToken = jwtTokenProvider.generateToken(user);
        return buildAuthResponse(user, accessToken, newRefreshToken);
    }

    @Override
    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .badgeNumber(user.getBadgeNumber())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .rank(user.getRank())
                        .stationName(user.getStationName())
                        .build())
                .build();
    }
}
