package pl.zai.backend.measurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.zai.backend.common.ApiErrors;

@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

    public static final String API_KEY_HEADER = "X-API-Key";
    private static final int DEFAULT_LIMIT = 1000;
    private static final int MAX_LIMIT = 10000;

    /** The series is not sent by the sensor; it is resolved from the sensor's API key. */
    public record MeasurementInput(@NotNull Double value, OffsetDateTime timestamp) {
    }

    public record MeasurementResponse(Long id, Long seriesId, Long sensorId, double value, Instant timestamp) {

        static MeasurementResponse from(Measurement measurement) {
            return new MeasurementResponse(measurement.getId(), measurement.getSeriesId(),
                    measurement.getSensorId(), measurement.getValue(), measurement.getTimestamp());
        }
    }

    private final MeasurementService measurementService;

    public MeasurementController(MeasurementService measurementService) {
        this.measurementService = measurementService;
    }

    @PostMapping
    public ResponseEntity<MeasurementResponse> submit(
            @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody MeasurementInput input) {
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiErrors.unauthorized("Missing " + API_KEY_HEADER + " header");
        }
        Instant timestamp = input.timestamp() != null ? input.timestamp().toInstant() : null;
        Measurement measurement = measurementService.submit(apiKey, input.value(), timestamp);
        return ResponseEntity.created(URI.create("/api/measurements/" + measurement.getId()))
                .body(MeasurementResponse.from(measurement));
    }

    @GetMapping
    public List<MeasurementResponse> search(@RequestParam(required = false) String series,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(defaultValue = "timestamp") String sort,
                                            @RequestParam(required = false) String limit) {
        boolean descending = switch (sort) {
            case "timestamp" -> false;
            case "-timestamp" -> true;
            default -> throw ApiErrors.badRequest("sort must be 'timestamp' or '-timestamp'");
        };
        MeasurementService.Query query = new MeasurementService.Query(parseSeriesIds(series),
                parseTime("from", from), parseTime("to", to), descending, parseLimit(limit));
        return measurementService.search(query).stream().map(MeasurementResponse::from).toList();
    }

    @GetMapping("/{measurementId}")
    public MeasurementResponse get(@PathVariable Long measurementId) {
        return MeasurementResponse.from(measurementService.get(measurementId));
    }

    /** Comma-separated ids, e.g. "1,3"; absent or empty means all series. */
    private static List<Long> parseSeriesIds(String series) {
        if (series == null) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (String part : series.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                throw ApiErrors.badRequest("series must be a comma-separated list of ids");
            }
        }
        return ids.isEmpty() ? null : ids;
    }

    private static Instant parseTime(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            // An unencoded "+01:00" offset arrives as " 01:00" after query-string decoding.
            return OffsetDateTime.parse(value.trim().replace(' ', '+')).toInstant();
        } catch (DateTimeParseException e) {
            throw ApiErrors.badRequest(name + " must be an ISO 8601 date-time with a time zone");
        }
    }

    private static int parseLimit(String limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        try {
            int parsed = Integer.parseInt(limit.trim());
            if (parsed >= 1 && parsed <= MAX_LIMIT) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        throw ApiErrors.badRequest("limit must be an integer between 1 and " + MAX_LIMIT);
    }
}
