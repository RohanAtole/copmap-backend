package in.copmap.repository;

import in.copmap.entity.LocationPing;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LocationPingRepository extends JpaRepository<LocationPing, Long> {

    List<LocationPing> findByOfficerIdOrderByRecordedAtDesc(UUID officerId, Pageable pageable);

    List<LocationPing> findByOperationIdOrderByRecordedAtDesc(UUID operationId, Pageable pageable);

    @Query("SELECT lp FROM LocationPing lp WHERE lp.officer.id = :officerId " +
           "AND lp.operation.id = :opId AND lp.recordedAt BETWEEN :from AND :to " +
           "ORDER BY lp.recordedAt ASC")
    List<LocationPing> findTrailForOperationPeriod(
            @Param("officerId") UUID officerId,
            @Param("opId") UUID operationId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT lp.officer.id, MAX(lp.recordedAt) FROM LocationPing lp " +
           "WHERE lp.operation.id = :opId GROUP BY lp.officer.id")
    List<Object[]> findLastPingTimePerOfficer(@Param("opId") UUID operationId);
}
