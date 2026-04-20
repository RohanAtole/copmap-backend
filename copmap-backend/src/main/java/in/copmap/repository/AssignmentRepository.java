package in.copmap.repository;

import in.copmap.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findByOperationId(UUID operationId);

    List<Assignment> findByOfficerId(UUID officerId);

    Optional<Assignment> findByOperationIdAndOfficerId(UUID operationId, UUID officerId);

    boolean existsByOperationIdAndOfficerId(UUID operationId, UUID officerId);

    List<Assignment> findByOfficerIdAndStatusIn(
            UUID officerId, List<Assignment.AssignmentStatus> statuses);

    @Query("SELECT a FROM Assignment a WHERE a.operation.id = :opId " +
           "AND a.status IN ('PENDING', 'ACKNOWLEDGED')")
    List<Assignment> findPendingForOperation(@Param("opId") UUID operationId);

    @Modifying
    @Query("UPDATE Assignment a SET a.status = :targetStatus WHERE a.operation.id = :opId " +
           "AND a.status = :sourceStatus")
    int markAbsentForOperation(@Param("opId") UUID operationId, 
                               @Param("sourceStatus") Assignment.AssignmentStatus sourceStatus, 
                               @Param("targetStatus") Assignment.AssignmentStatus targetStatus);

    @Query("SELECT COUNT(a) FROM Assignment a WHERE a.operation.id = :opId " +
           "AND a.status = :status")
    long countByOperationIdAndStatus(
            @Param("opId") UUID operationId,
            @Param("status") Assignment.AssignmentStatus status);
}
