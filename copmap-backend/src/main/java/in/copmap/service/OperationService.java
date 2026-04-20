package in.copmap.service;

import in.copmap.dto.request.*;
import in.copmap.dto.response.*;
import in.copmap.entity.Operation;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface OperationService {
    OperationResponse createOperation(CreateOperationRequest request);
    OperationResponse publishOperation(UUID operationId);
    OperationResponse startOperation(UUID operationId);
    OperationResponse closeOperation(UUID operationId, CloseOperationRequest request);
    List<AssignmentResponse> assignOfficers(UUID operationId, AssignOfficersRequest request);
    AssignmentResponse acknowledgeAssignment(UUID operationId);
    AssignmentResponse checkIn(UUID operationId);
    AssignmentResponse checkOut(UUID operationId);
    Page<OperationResponse> getOperations(Operation.OperationType type,
                                           Operation.OperationStatus status,
                                           int page, int size);
    OperationResponse getOperation(UUID id);
}
