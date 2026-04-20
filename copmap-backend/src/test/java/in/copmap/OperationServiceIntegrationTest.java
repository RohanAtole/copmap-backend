package in.copmap;

import in.copmap.dto.request.CreateOperationRequest;
import in.copmap.dto.response.OperationResponse;
import in.copmap.entity.Operation;
import in.copmap.entity.User;
import in.copmap.repository.OperationRepository;
import in.copmap.repository.UserRepository;
import in.copmap.security.SecurityUtils;
import in.copmap.service.OperationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Operation Service Integration Tests")
class OperationServiceIntegrationTest {

    @Autowired private OperationService operationService;
    @Autowired private UserRepository userRepository;
    @Autowired private OperationRepository operationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User stationOfficer;

    @BeforeEach
    void setUp() {
        stationOfficer = User.builder()
                .badgeNumber("TEST001")
                .fullName("Test Officer")
                .phone("9876543210")
                .passwordHash(passwordEncoder.encode("Test@123"))
                .role(User.UserRole.STATION_OFFICER)
                .status(User.UserStatus.ACTIVE)
                .stationName("Test Station")
                .build();
        stationOfficer = userRepository.save(stationOfficer);

        // Simulate logged-in user
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(stationOfficer, null,
                        stationOfficer.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should create a PATROL operation in DRAFT status")
    void testCreatePatrolOperation() {
        CreateOperationRequest request = new CreateOperationRequest();
        request.setType(Operation.OperationType.PATROL);
        request.setTitle("Night Patrol - Sector 7");
        request.setLocationName("Sector 7, New Delhi");
        request.setPlannedStart(Instant.now().plusSeconds(3600));
        request.setPlannedEnd(Instant.now().plusSeconds(7200));

        OperationResponse response = operationService.createOperation(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(Operation.OperationStatus.DRAFT);
        assertThat(response.getType()).isEqualTo(Operation.OperationType.PATROL);
        assertThat(response.getTitle()).isEqualTo("Night Patrol - Sector 7");
        assertThat(response.getStationName()).isEqualTo("Test Station");
    }

    @Test
    @DisplayName("Should create a BANDOBAST operation with checkpoints")
    void testCreateBandobastWithCheckpoints() {
        CreateOperationRequest request = new CreateOperationRequest();
        request.setType(Operation.OperationType.BANDOBAST);
        request.setTitle("Republic Day Bandobast");
        request.setLocationName("Parade Ground, Connaught Place");
        request.setPlannedStart(Instant.now().plusSeconds(3600));
        request.setPlannedEnd(Instant.now().plusSeconds(14400));
        request.setShiftType(Operation.ShiftType.MORNING);

        var cp = new CreateOperationRequest.CheckpointRequest();
        cp.setName("Main Gate");
        cp.setLatitude(28.6139);
        cp.setLongitude(77.2090);
        cp.setRadiusMeters(100);
        request.setCheckpoints(java.util.List.of(cp));

        OperationResponse response = operationService.createOperation(request);

        assertThat(response.getCheckpoints()).hasSize(1);
        assertThat(response.getCheckpoints().get(0).getName()).isEqualTo("Main Gate");
    }

    @Test
    @DisplayName("Should reject operation with end time before start time")
    void testInvalidDateRange() {
        CreateOperationRequest request = new CreateOperationRequest();
        request.setType(Operation.OperationType.PATROL);
        request.setTitle("Invalid Dates");
        request.setLocationName("Test Location");
        request.setPlannedStart(Instant.now().plusSeconds(7200));
        request.setPlannedEnd(Instant.now().plusSeconds(3600));  // end before start

        assertThatThrownBy(() -> operationService.createOperation(request))
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    @DisplayName("Should not allow publishing an operation without officers")
    void testPublishWithoutOfficers() {
        CreateOperationRequest request = new CreateOperationRequest();
        request.setType(Operation.OperationType.NAKABANDI);
        request.setTitle("Nakabandi - NH48");
        request.setLocationName("NH-48, Gurugram");
        request.setPlannedStart(Instant.now().plusSeconds(3600));
        request.setPlannedEnd(Instant.now().plusSeconds(7200));

        OperationResponse created = operationService.createOperation(request);

        assertThatThrownBy(() -> operationService.publishOperation(created.getId()))
                .hasMessageContaining("no assigned officers");
    }
}
