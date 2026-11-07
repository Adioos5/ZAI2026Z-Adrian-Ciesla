package pl.zai.backend.measurement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "measurements", indexes = @Index(columnList = "series_id, measured_at"))
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long seriesId;

    /** Plain column, not a foreign key: measurements outlive the sensor that sent them. */
    private Long sensorId;

    @Column(name = "measured_value", nullable = false)
    private double value;

    @Column(name = "measured_at", nullable = false)
    private Instant timestamp;

    protected Measurement() {
    }

    public Measurement(Long seriesId, Long sensorId, double value, Instant timestamp) {
        this.seriesId = seriesId;
        this.sensorId = sensorId;
        this.value = value;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public Long getSensorId() {
        return sensorId;
    }

    public double getValue() {
        return value;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
