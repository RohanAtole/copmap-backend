package in.copmap.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Operation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "operation_type")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    private OperationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "operation_status")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Builder.Default
    private OperationStatus status = OperationStatus.DRAFT;

    @Column(name = "planned_start", nullable = false)
    private Instant plannedStart;

    @Column(name = "planned_end", nullable = false)
    private Instant plannedEnd;

    @Column(name = "actual_start")
    private Instant actualStart;

    @Column(name = "actual_end")
    private Instant actualEnd;

    @Column(name = "area_geojson", columnDefinition = "TEXT")
    private String areaGeoJson;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "station_name", nullable = false, length = 100)
    private String stationName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(name = "closure_notes")
    private String closureNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", columnDefinition = "shift_type")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    private ShiftType shiftType;

    // ── Relationships ──────────────────────────────────────────────────

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Assignment> assignments = new ArrayList<>();

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Checkpoint> checkpoints = new ArrayList<>();

    @OneToOne(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
    private PatrolConfig patrolConfig;

    // ── Enums ─────────────────────────────────────────────────────────

    public enum OperationType {
        PATROL, BANDOBAST, NAKABANDI
    }

    public enum OperationStatus {
        DRAFT, PUBLISHED, ACTIVE, COMPLETED, CANCELLED
    }

    public enum ShiftType {
        MORNING, AFTERNOON, NIGHT, FULL_DAY
    }

    // ── Business logic helpers ─────────────────────────────────────────

    public boolean isModifiable() {
        return status == OperationStatus.DRAFT || status == OperationStatus.PUBLISHED;
    }

    public boolean isActive() {
        return status == OperationStatus.ACTIVE;
    }
}
