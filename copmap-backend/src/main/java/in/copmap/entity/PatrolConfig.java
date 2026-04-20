package in.copmap.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patrol_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatrolConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private Operation operation;

    @Column(name = "route_geojson", columnDefinition = "TEXT")
    private String routeGeoJson;

    @Column(name = "beat_name", length = 100)
    private String beatName;

    @Column(name = "expected_rounds")
    @Builder.Default
    private Integer expectedRounds = 1;

    @Column(name = "round_interval")
    private Integer roundInterval;  // minutes

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
