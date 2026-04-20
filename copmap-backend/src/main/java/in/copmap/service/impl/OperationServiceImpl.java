package in.copmap.service.impl;

import in.copmap.dto.request.*;
import in.copmap.dto.response.*;
import in.copmap.entity.*;
import in.copmap.exception.CopMapException;
import in.copmap.repository.*;
import in.copmap.security.SecurityUtils;
import in.copmap.service.AlertService;
import in.copmap.service.NotificationService;
import in.copmap.service.OperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationServiceImpl implements OperationService {

    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final CheckpointRepository checkpointRepository;
    private final AlertRepository alertRepository;
    private final AuditLogRepository auditLogRepository;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // ── CREATE ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OperationResponse createOperation(CreateOperationRequest request) {
        User creator = SecurityUtils.getCurrentUser();

        // Validate temporal logic
        if (request.getPlannedEnd().isBefore(request.getPlannedStart())) {
            throw new CopMapException("INVALID_DATES", "End time must be after start time");
        }

        Operation operation = Operation.builder()
                .type(request.getType())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(Operation.OperationStatus.DRAFT)
                .plannedStart(request.getPlannedStart())
                .plannedEnd(request.getPlannedEnd())
                .areaGeoJson(request.getAreaGeoJson())
                .locationName(request.getLocationName())
                .stationName(creator.getStationName())
                .createdBy(creator)
                .shiftType(request.getShiftType())
                .build();

        // Patrol-specific config
        if (request.getPatrolConfig() != null && request.getType() == Operation.OperationType.PATROL) {
            PatrolConfig pc = PatrolConfig.builder()
                    .operation(operation)
                    .beatName(request.getPatrolConfig().getBeatName())
                    .routeGeoJson(request.getPatrolConfig().getRouteGeoJson())
                    .expectedRounds(request.getPatrolConfig().getExpectedRounds())
                    .roundInterval(request.getPatrolConfig().getRoundInterval())
                    .build();
            operation.setPatrolConfig(pc);
        }

        // Checkpoints
        if (request.getCheckpoints() != null && !request.getCheckpoints().isEmpty()) {
            List<Checkpoint> checkpoints = request.getCheckpoints().stream()
                    .map(cr -> Checkpoint.builder()
                            .operation(operation)
                            .name(cr.getName())
                            .latitude(cr.getLatitude())
                            .longitude(cr.getLongitude())
                            .radiusMeters(cr.getRadiusMeters() != null ? cr.getRadiusMeters() : 50)
                            .sequenceOrder(cr.getSequenceOrder() != null ? cr.getSequenceOrder() : 0)
                            .notes(cr.getNotes())
                            .build())
                    .collect(Collectors.toList());
            operation.setCheckpoints(checkpoints);
        }

        Operation savedOperation = operationRepository.save(operation);

        // Audit
        auditOperation(savedOperation, "CREATED", null, creator);

        log.info("Operation {} ({}) created by {}", savedOperation.getId(), savedOperation.getType(), creator.getBadgeNumber());
        return toResponse(savedOperation);
    }

    // ── PUBLISH ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public OperationResponse publishOperation(UUID operationId) {
        Operation operation = getOperationOrThrow(operationId);
        User actor = SecurityUtils.getCurrentUser();

        assertStationMatch(operation, actor);
        if (operation.getStatus() != Operation.OperationStatus.DRAFT) {
            throw new CopMapException("INVALID_STATUS", "Only DRAFT operations can be published");
        }
        if (operation.getAssignments().isEmpty()) {
            throw new CopMapException("NO_ASSIGNMENTS", "Cannot publish an operation with no assigned officers");
        }

        Operation.OperationStatus old = operation.getStatus();
        operation.setStatus(Operation.OperationStatus.PUBLISHED);
        Operation savedOperation = operationRepository.save(operation);

        // Notify all assigned officers
        List<Assignment> assignments = assignmentRepository.findByOperationId(operationId);
        String title = savedOperation.getTitle();
        assignments.forEach(a -> notificationService.notifyOfficer(a.getOfficer(),
                "Operation Published",
                "You have been assigned to: " + title));

        auditStatusChange(operation, old, Operation.OperationStatus.PUBLISHED, actor);
        return toResponse(operation);
    }

    // ── START ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OperationResponse startOperation(UUID operationId) {
        Operation operation = getOperationOrThrow(operationId);
        User actor = SecurityUtils.getCurrentUser();

        assertStationMatch(operation, actor);
        if (operation.getStatus() != Operation.OperationStatus.PUBLISHED) {
            throw new CopMapException("INVALID_STATUS", "Operation must be in PUBLISHED state to start");
        }

        Operation.OperationStatus old = operation.getStatus();
        operation.setStatus(Operation.OperationStatus.ACTIVE);
        operation.setActualStart(Instant.now());
        operation = operationRepository.save(operation);

        broadcastOperationUpdate(operation);
        auditStatusChange(operation, old, Operation.OperationStatus.ACTIVE, actor);
        return toResponse(operation);
    }

    // ── CLOSE ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OperationResponse closeOperation(UUID operationId, CloseOperationRequest request) {
        Operation operation = getOperationOrThrow(operationId);
        User actor = SecurityUtils.getCurrentUser();

        assertStationMatch(operation, actor);
        if (operation.getStatus() != Operation.OperationStatus.ACTIVE) {
            throw new CopMapException("INVALID_STATUS", "Only ACTIVE operations can be closed");
        }

        Operation.OperationStatus old = operation.getStatus();
        operation.setStatus(Operation.OperationStatus.COMPLETED);
        operation.setActualEnd(Instant.now());
        operation.setClosedBy(actor);
        operation.setClosureNotes(request.getClosureNotes());
        operation = operationRepository.save(operation);

        // Mark any PENDING assignments as ABSENT
        int absentCount = assignmentRepository.markAbsentForOperation(operationId, Assignment.AssignmentStatus.PENDING, Assignment.AssignmentStatus.ABSENT);
        if (absentCount > 0) {
            log.warn("{} officers marked absent for operation {}", absentCount, operationId);
        }

        broadcastOperationUpdate(operation);
        auditStatusChange(operation, old, Operation.OperationStatus.COMPLETED, actor);
        return toResponse(operation);
    }

    // ── ASSIGN OFFICERS ───────────────────────────────────────────────

    @Override
    @Transactional
    public List<AssignmentResponse> assignOfficers(UUID operationId, AssignOfficersRequest request) {
        Operation operation = getOperationOrThrow(operationId);
        User actor = SecurityUtils.getCurrentUser();

        assertStationMatch(operation, actor);
        if (!operation.isModifiable()) {
            throw new CopMapException("INVALID_STATUS",
                    "Officers can only be assigned to DRAFT or PUBLISHED operations");
        }

        List<Assignment> created = new ArrayList<>();
        for (AssignOfficersRequest.OfficerAssignment oa : request.getOfficers()) {
            User officer = userRepository.findById(oa.getOfficerId())
                    .orElseThrow(() -> new CopMapException("OFFICER_NOT_FOUND",
                            "Officer not found: " + oa.getOfficerId()));

            if (assignmentRepository.existsByOperationIdAndOfficerId(operationId, officer.getId())) {
                log.warn("Officer {} already assigned to operation {}", officer.getBadgeNumber(), operationId);
                continue;
            }

            Checkpoint checkpoint = null;
            if (oa.getCheckpointId() != null) {
                checkpoint = checkpointRepository.findById(oa.getCheckpointId())
                        .orElseThrow(() -> new CopMapException("CHECKPOINT_NOT_FOUND",
                                "Checkpoint not found: " + oa.getCheckpointId()));
                if (!checkpoint.getOperation().getId().equals(operationId)) {
                    throw new CopMapException("CHECKPOINT_MISMATCH", "Checkpoint does not belong to this operation");
                }
            }

            Assignment a = Assignment.builder()
                    .operation(operation)
                    .officer(officer)
                    .assignedBy(actor)
                    .checkpoint(checkpoint)
                    .dutyRole(oa.getDutyRole())
                    .dutyStart(operation.getPlannedStart())
                    .dutyEnd(operation.getPlannedEnd())
                    .build();

            created.add(assignmentRepository.save(a));
        }

        auditOperation(operation, "OFFICERS_ASSIGNED", null, actor);
        return created.stream().map(this::toAssignmentResponse).collect(Collectors.toList());
    }

    // ── OFFICER SELF-SERVICE: ACKNOWLEDGE, CHECK-IN, CHECK-OUT ────────

    @Override
    @Transactional
    public AssignmentResponse acknowledgeAssignment(UUID operationId) {
        User officer = SecurityUtils.getCurrentUser();
        Assignment assignment = getAssignmentOrThrow(operationId, officer.getId());

        if (assignment.getStatus() != Assignment.AssignmentStatus.PENDING) {
            throw new CopMapException("ALREADY_ACKNOWLEDGED", "Assignment already acknowledged");
        }

        assignment.setStatus(Assignment.AssignmentStatus.ACKNOWLEDGED);
        assignment.setAcknowledgedAt(Instant.now());
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public AssignmentResponse checkIn(UUID operationId) {
        User officer = SecurityUtils.getCurrentUser();
        Assignment assignment = getAssignmentOrThrow(operationId, officer.getId());

        if (assignment.getStatus() == Assignment.AssignmentStatus.ON_DUTY) {
            throw new CopMapException("ALREADY_CHECKED_IN", "Already checked in");
        }

        assignment.setStatus(Assignment.AssignmentStatus.ON_DUTY);
        assignment.setCheckedInAt(Instant.now());
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional
    public AssignmentResponse checkOut(UUID operationId) {
        User officer = SecurityUtils.getCurrentUser();
        Assignment assignment = getAssignmentOrThrow(operationId, officer.getId());

        if (assignment.getStatus() != Assignment.AssignmentStatus.ON_DUTY) {
            throw new CopMapException("NOT_ON_DUTY", "Cannot check out without checking in first");
        }

        assignment.setStatus(Assignment.AssignmentStatus.COMPLETED);
        assignment.setCheckedOutAt(Instant.now());
        return toAssignmentResponse(assignmentRepository.save(assignment));
    }

    // ── QUERY ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<OperationResponse> getOperations(
            Operation.OperationType type, Operation.OperationStatus status,
            int page, int size) {

        User user = SecurityUtils.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Operation> ops;
        if (type != null) {
            ops = operationRepository.findByTypeAndStationName(type, user.getStationName(), pageable);
        } else {
            ops = operationRepository.findByStationName(user.getStationName(), pageable);
        }

        return ops.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OperationResponse getOperation(UUID id) {
        return toResponse(getOperationOrThrow(id));
    }

    // ── HELPERS ───────────────────────────────────────────────────────

    private Operation getOperationOrThrow(UUID id) {
        return operationRepository.findById(id)
                .orElseThrow(() -> new CopMapException("OPERATION_NOT_FOUND", "Operation not found: " + id));
    }

    private Assignment getAssignmentOrThrow(UUID operationId, UUID officerId) {
        return assignmentRepository.findByOperationIdAndOfficerId(operationId, officerId)
                .orElseThrow(() -> new CopMapException("ASSIGNMENT_NOT_FOUND",
                        "No assignment found for this officer on this operation"));
    }

    private void assertStationMatch(Operation op, User user) {
        if (user.getRole() != User.UserRole.SUPER_ADMIN
                && !op.getStationName().equals(user.getStationName())) {
            throw new AccessDeniedException("You can only manage operations for your own station");
        }
    }

    private void broadcastOperationUpdate(Operation operation) {
        messagingTemplate.convertAndSend(
                "/topic/operations/" + operation.getId() + "/status",
                Map.of("status", operation.getStatus(), "updatedAt", Instant.now()));
    }

    private void auditStatusChange(Operation op,
                                    Operation.OperationStatus oldStatus,
                                    Operation.OperationStatus newStatus,
                                    User actor) {
        auditLogRepository.save(AuditLog.builder()
                .entityType("OPERATION")
                .entityId(op.getId())
                .action("STATUS_CHANGED")
                .actor(actor)
                .oldValue(Map.of("status", oldStatus.name()))
                .newValue(Map.of("status", newStatus.name()))
                .build());
    }

    private void auditOperation(Operation op, String action,
                                 Map<String, Object> oldValue, User actor) {
        auditLogRepository.save(AuditLog.builder()
                .entityType("OPERATION")
                .entityId(op.getId())
                .action(action)
                .actor(actor)
                .newValue(Map.of("type", op.getType().name(), "title", op.getTitle()))
                .build());
    }

    // ── MAPPING ───────────────────────────────────────────────────────

    private OperationResponse toResponse(Operation op) {
        long onDuty  = assignmentRepository.countByOperationIdAndStatus(
                op.getId(), Assignment.AssignmentStatus.ON_DUTY);
        long pending = assignmentRepository.countByOperationIdAndStatus(
                op.getId(), Assignment.AssignmentStatus.PENDING);
        long openAlerts = alertRepository.countByOperationIdAndStatus(
                op.getId(), Alert.AlertStatus.OPEN);

        OperationResponse.OperationResponseBuilder builder = OperationResponse.builder()
                .id(op.getId())
                .type(op.getType())
                .title(op.getTitle())
                .description(op.getDescription())
                .status(op.getStatus())
                .plannedStart(op.getPlannedStart())
                .plannedEnd(op.getPlannedEnd())
                .actualStart(op.getActualStart())
                .actualEnd(op.getActualEnd())
                .areaGeoJson(op.getAreaGeoJson())
                .locationName(op.getLocationName())
                .stationName(op.getStationName())
                .shiftType(op.getShiftType())
                .totalAssignments(op.getAssignments().size())
                .onDutyCount((int) onDuty)
                .pendingCount((int) pending)
                .openAlertsCount(openAlerts)
                .createdAt(op.getCreatedAt())
                .updatedAt(op.getUpdatedAt())
                .createdBy(OperationResponse.CreatedByInfo.builder()
                        .id(op.getCreatedBy().getId())
                        .badgeNumber(op.getCreatedBy().getBadgeNumber())
                        .fullName(op.getCreatedBy().getFullName())
                        .rank(op.getCreatedBy().getRank())
                        .build());

        if (op.getPatrolConfig() != null) {
            PatrolConfig pc = op.getPatrolConfig();
            builder.patrolConfig(OperationResponse.PatrolConfigInfo.builder()
                    .beatName(pc.getBeatName())
                    .routeGeoJson(pc.getRouteGeoJson())
                    .expectedRounds(pc.getExpectedRounds())
                    .roundInterval(pc.getRoundInterval())
                    .build());
        }

        if (op.getCheckpoints() != null && !op.getCheckpoints().isEmpty()) {
            builder.checkpoints(op.getCheckpoints().stream()
                    .map(c -> OperationResponse.CheckpointInfo.builder()
                            .id(c.getId())
                            .name(c.getName())
                            .latitude(c.getLatitude())
                            .longitude(c.getLongitude())
                            .radiusMeters(c.getRadiusMeters())
                            .sequenceOrder(c.getSequenceOrder())
                            .notes(c.getNotes())
                            .build())
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private AssignmentResponse toAssignmentResponse(Assignment a) {
        return AssignmentResponse.builder()
                .id(a.getId())
                .operationId(a.getOperation().getId())
                .operationTitle(a.getOperation().getTitle())
                .officer(AssignmentResponse.OfficerInfo.builder()
                        .id(a.getOfficer().getId())
                        .badgeNumber(a.getOfficer().getBadgeNumber())
                        .fullName(a.getOfficer().getFullName())
                        .rank(a.getOfficer().getRank())
                        .build())
                .status(a.getStatus())
                .dutyRole(a.getDutyRole())
                .dutyStart(a.getDutyStart())
                .dutyEnd(a.getDutyEnd())
                .acknowledgedAt(a.getAcknowledgedAt())
                .checkedInAt(a.getCheckedInAt())
                .checkedOutAt(a.getCheckedOutAt())
                .checkpoint(a.getCheckpoint() != null
                        ? AssignmentResponse.CheckpointInfo.builder()
                                .id(a.getCheckpoint().getId())
                                .name(a.getCheckpoint().getName())
                                .build()
                        : null)
                .build();
    }
}
