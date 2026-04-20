package in.copmap.repository;

import in.copmap.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByBadgeNumber(String badgeNumber);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByBadgeNumber(String badgeNumber);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<User> findByStationNameAndStatusAndRole(
            String stationName, User.UserStatus status, User.UserRole role);

    @Query("SELECT u FROM User u WHERE u.stationName = :station AND u.status = :userStatus " +
           "AND u.id NOT IN (" +
           "  SELECT a.officer.id FROM Assignment a " +
           "  WHERE a.operation.status IN :opStatuses " +
           "  AND a.status NOT IN :assignStatuses" +
           ")")
    List<User> findAvailableOfficers(@Param("station") String stationName,
                                     @Param("userStatus") User.UserStatus userStatus,
                                     @Param("opStatuses") List<in.copmap.entity.Operation.OperationStatus> opStatuses,
                                     @Param("assignStatuses") List<in.copmap.entity.Assignment.AssignmentStatus> assignStatuses);

    Page<User> findByStationName(String stationName, Pageable pageable);
}
