package in.copmap.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id")
    private Operation operation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id")
    private User officer;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, columnDefinition = "alert_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, columnDefinition = "alert_severity")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private AlertSeverity severity = AlertSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "alert_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message")
    private String message;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public enum AlertType {
        OFFICER_OFFLINE, BOUNDARY_BREACH, SOS, OPERATION_DELAYED, CUSTOM
    }

    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlertStatus {
        OPEN, ACKNOWLEDGED, RESOLVED
    }
}
