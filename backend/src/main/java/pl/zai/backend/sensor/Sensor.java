package pl.zai.backend.sensor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sensors")
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long seriesId;

    /** SHA-256 of the API key; the key itself is shown once, at registration. */
    @Column(nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastMeasurementAt;

    protected Sensor() {
    }

    public Sensor(String name, Long seriesId, String apiKeyHash, Instant createdAt) {
        this.name = name;
        this.seriesId = seriesId;
        this.apiKeyHash = apiKeyHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMeasurementAt() {
        return lastMeasurementAt;
    }

    public void setLastMeasurementAt(Instant lastMeasurementAt) {
        this.lastMeasurementAt = lastMeasurementAt;
    }
}
