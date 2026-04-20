package in.copmap.controller;

import in.copmap.dto.request.*;
import in.copmap.dto.response.*;
import in.copmap.entity.Operation;
import in.copmap.service.OperationService;
import in.copmap.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class OperationController {

    private final OperationService operationService;
    private final ReportService reportService;

    // ── CRUD ─────────────────────────────────────────────────────────

    /**
     * POST /api/v1/operations
     * Create a new PATROL, BANDOBAST, or NAKABANDI operation.
     * Roles: STATION_OFFICER, SUPER_ADMIN
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OperationResponse>> create(
            @Valid @RequestBody CreateOperationRequest request) {
        OperationResponse response = operationService.createOperation(request);
        return ResponseEntity.status(201).body(ApiResponse.ok("Operation created", response));
    }

    /**
     * GET /api/v1/operations?type=PATROL&status=ACTIVE&page=0&size=20
     * List operations for the authenticated officer's station.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OperationResponse>>> list(
            @RequestParam(required = false) Operation.OperationType type,
            @RequestParam(required = false) Operation.OperationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<OperationResponse> ops = operationService.getOperations(type, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok(ops));
    }

    /**
     * GET /api/v1/operations/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OperationResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(operationService.getOperation(id)));
    }

    // ── LIFECYCLE ─────────────────────────────────────────────────────

    /**
     * POST /api/v1/operations/{id}/publish
     * Move from DRAFT → PUBLISHED. Notifies assigned officers.
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OperationResponse>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Operation published", operationService.publishOperation(id)));
    }

    /**
     * POST /api/v1/operations/{id}/start
     * Move from PUBLISHED → ACTIVE. Records actual start time.
     */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OperationResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Operation started", operationService.startOperation(id)));
    }

    /**
     * POST /api/v1/operations/{id}/close
     * Move from ACTIVE → COMPLETED. Records actual end time and closure notes.
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OperationResponse>> close(
            @PathVariable UUID id,
            @Valid @RequestBody CloseOperationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Operation closed", operationService.closeOperation(id, request)));
    }

    // ── ASSIGNMENTS ───────────────────────────────────────────────────

    /**
     * POST /api/v1/operations/{id}/assignments
     * Bulk-assign officers to an operation.
     */
    @PostMapping("/{id}/assignments")
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssignOfficersRequest request) {
        List<AssignmentResponse> assignments = operationService.assignOfficers(id, request);
        return ResponseEntity.status(201).body(ApiResponse.ok("Officers assigned", assignments));
    }

    // ── OFFICER SELF-SERVICE ──────────────────────────────────────────

    /**
     * POST /api/v1/operations/{id}/acknowledge
     * Officer acknowledges their assignment (having read the order).
     */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<ApiResponse<AssignmentResponse>> acknowledge(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Assignment acknowledged",
                operationService.acknowledgeAssignment(id)));
    }

    /**
     * POST /api/v1/operations/{id}/check-in
     * Officer marks themselves as on-duty at their post.
     */
    @PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResponse<AssignmentResponse>> checkIn(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Checked in", operationService.checkIn(id)));
    }

    /**
     * POST /api/v1/operations/{id}/check-out
     * Officer marks duty as complete.
     */
    @PostMapping("/{id}/check-out")
    public ResponseEntity<ApiResponse<AssignmentResponse>> checkOut(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Checked out", operationService.checkOut(id)));
    }

    // ── REPORT ───────────────────────────────────────────────────────

    /**
     * GET /api/v1/operations/{id}/report.pdf
     * Generate and download a PDF operation report.
     * Roles: STATION_OFFICER, SUPER_ADMIN
     */
    @GetMapping(value = "/{id}/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('STATION_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID id) {
        byte[] pdf = reportService.generateOperationReport(id);
        String filename = "copmap-operation-" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
