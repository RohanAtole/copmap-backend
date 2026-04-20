package in.copmap.repository;

import in.copmap.entity.Operation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OperationRepository extends JpaRepository<Operation, UUID> {

    Page<Operation> findByStationName(String stationName, Pageable pageable);

    Page<Operation> findByTypeAndStationName(
            Operation.OperationType type, String stationName, Pageable pageable);

    Page<Operation> findByStatus(Operation.OperationStatus status, Pageable pageable);

    List<Operation> findByStatusIn(List<Operation.OperationStatus> statuses);

    @Query("SELECT o FROM Operation o WHERE o.status = :status " +
           "AND o.plannedStart <= :now AND o.stationName = :station")
    List<Operation> findDueOperations(@Param("status") Operation.OperationStatus status,
                                      @Param("now") Instant now,
                                      @Param("station") String stationName);

    @Query("SELECT o FROM Operation o WHERE o.status = :status " +
           "AND o.plannedEnd < :threshold")
    List<Operation> findOverdueActiveOperations(@Param("status") Operation.OperationStatus status,
                                                @Param("threshold") Instant threshold);

    @Query("SELECT COUNT(o) FROM Operation o WHERE o.stationName = :station " +
           "AND o.status = :status AND o.type = :type")
    long countByStationAndStatusAndType(
            @Param("station") String station,
            @Param("status") Operation.OperationStatus status,
            @Param("type") Operation.OperationType type);
}
