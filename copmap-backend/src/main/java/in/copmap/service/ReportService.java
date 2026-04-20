package in.copmap.service;

import java.util.UUID;

public interface ReportService {
    byte[] generateOperationReport(UUID operationId);
}
