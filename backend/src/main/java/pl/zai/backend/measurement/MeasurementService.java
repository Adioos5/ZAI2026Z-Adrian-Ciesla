package pl.zai.backend.measurement;

import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zai.backend.common.ApiErrors;
import pl.zai.backend.sensor.Sensor;
import pl.zai.backend.sensor.SensorService;
import pl.zai.backend.series.Series;
import pl.zai.backend.series.SeriesRepository;

@Service
public class MeasurementService {

    /** How far ahead of the server clock a submitted timestamp may be. */
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    public record Query(List<Long> seriesIds, Instant from, Instant to, boolean descending, int limit) {
    }

    private final MeasurementRepository measurementRepository;
    private final SeriesRepository seriesRepository;
    private final SensorService sensorService;

    public MeasurementService(MeasurementRepository measurementRepository, SeriesRepository seriesRepository,
                              SensorService sensorService) {
        this.measurementRepository = measurementRepository;
        this.seriesRepository = seriesRepository;
        this.sensorService = sensorService;
    }

    /** Stores a value sent by a sensor; the target series is the one the sensor's key is bound to. */
    @Transactional
    public Measurement submit(String apiKey, double value, Instant timestamp) {
        Sensor sensor = sensorService.findByApiKey(apiKey)
                .orElseThrow(() -> ApiErrors.unauthorized("Invalid sensor API key"));
        Series series = seriesRepository.findById(sensor.getSeriesId())
                .orElseThrow(() -> ApiErrors.unauthorized("Sensor's series no longer exists"));

        Instant now = Instant.now();
        Instant effectiveTimestamp = timestamp != null ? timestamp : now;
        if (effectiveTimestamp.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw ApiErrors.unprocessable("timestamp must not be more than 5 minutes in the future");
        }
        if (value < series.getMinValue() || value > series.getMaxValue()) {
            throw ApiErrors.unprocessable("value " + value + " is outside the series range ["
                    + series.getMinValue() + ", " + series.getMaxValue() + "]");
        }

        Measurement measurement = measurementRepository.save(
                new Measurement(series.getId(), sensor.getId(), value, effectiveTimestamp));
        if (sensor.getLastMeasurementAt() == null || sensor.getLastMeasurementAt().isBefore(effectiveTimestamp)) {
            sensor.setLastMeasurementAt(effectiveTimestamp);
        }
        return measurement;
    }

    @Transactional(readOnly = true)
    public List<Measurement> search(Query query) {
        Specification<Measurement> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.seriesIds() != null) {
                predicates.add(root.get("seriesId").in(query.seriesIds()));
            }
            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), query.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Sort.Direction direction = query.descending() ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, "timestamp").and(Sort.by(direction, "id"));
        return measurementRepository.findAll(spec, PageRequest.of(0, query.limit(), sort)).getContent();
    }

    @Transactional(readOnly = true)
    public Measurement get(Long id) {
        return measurementRepository.findById(id)
                .orElseThrow(() -> ApiErrors.notFound("Measurement " + id + " not found"));
    }
}
