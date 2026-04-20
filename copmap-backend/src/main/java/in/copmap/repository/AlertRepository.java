package in.copmap.repository;

import in.copmap.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    Page<Alert> findByOperationId(UUID operationId, Pageable pageable);

    Page<Alert> findByOfficerId(UUID officerId, Pageable pageable);

    List<Alert> findByStatusAndSeverityIn(
            Alert.AlertStatus status, List<Alert.AlertSeverity> severities);

    long countByOperationIdAndStatus(UUID operationId, Alert.AlertStatus status);
}
