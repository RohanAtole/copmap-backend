package in.copmap.controller;

import in.copmap.dto.request.CreateUserRequest;
import in.copmap.dto.response.ApiResponse;
import in.copmap.entity.User;
import in.copmap.exception.CopMapException;
import in.copmap.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * POST /api/v1/users
     * Roles: SUPER_ADMIN (create any role), STATION_OFFICER (create OFFICERs only)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserSummary>> create(
            @Valid @RequestBody CreateUserRequest request) {

        if (userRepository.existsByBadgeNumber(request.getBadgeNumber())) {
            throw new CopMapException("BADGE_EXISTS", "Badge number already registered");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new CopMapException("EMAIL_EXISTS", "Email already registered");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new CopMapException("PHONE_EXISTS", "Phone number already registered");
        }

        User user = User.builder()
                .badgeNumber(request.getBadgeNumber())
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(User.UserStatus.ACTIVE)
                .rank(request.getRank())
                .stationName(request.getStationName())
                .build();

        user = userRepository.save(user);
        return ResponseEntity.status(201).body(ApiResponse.ok("User created", toSummary(user)));
    }

    /**
     * GET /api/v1/users?station=XYZ&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserSummary>>> list(
            @RequestParam(required = false) String station,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName"));
        Page<User> users = station != null
                ? userRepository.findByStationName(station, pageable)
                : userRepository.findAll(pageable);

        return ResponseEntity.ok(ApiResponse.ok(users.map(this::toSummary)));
    }

    /**
     * GET /api/v1/users/available?station=XYZ
     * Returns officers not currently assigned to any active operation.
     */
    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserSummary>>> available(
            @RequestParam String station) {
        List<User> users = userRepository.findAvailableOfficers(
                station,
                User.UserStatus.ACTIVE,
                List.of(in.copmap.entity.Operation.OperationStatus.PUBLISHED, in.copmap.entity.Operation.OperationStatus.ACTIVE),
                List.of(in.copmap.entity.Assignment.AssignmentStatus.COMPLETED, in.copmap.entity.Assignment.AssignmentStatus.ABSENT)
        );
        return ResponseEntity.ok(ApiResponse.ok(users.stream().map(this::toSummary).toList()));
    }

    /**
     * GET /api/v1/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserSummary>> get(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CopMapException("USER_NOT_FOUND", "User not found"));
        return ResponseEntity.ok(ApiResponse.ok(toSummary(user)));
    }

    /**
     * PATCH /api/v1/users/{id}/status?status=SUSPENDED
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserSummary>> updateStatus(
            @PathVariable UUID id,
            @RequestParam User.UserStatus status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new CopMapException("USER_NOT_FOUND", "User not found"));
        user.setStatus(status);
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("Status updated", toSummary(user)));
    }

    // ── DTO ───────────────────────────────────────────────────────────

    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getBadgeNumber(), u.getFullName(),
                u.getRole(), u.getRank(), u.getStationName(), u.getStatus(), u.getPhone(), u.getEmail());
    }

    public record UserSummary(
            UUID id,
            String badgeNumber,
            String fullName,
            User.UserRole role,
            String rank,
            String stationName,
            User.UserStatus status,
            String phone,
            String email
    ) {}
}
