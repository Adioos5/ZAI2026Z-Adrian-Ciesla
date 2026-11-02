package pl.zai.backend.measurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.zai.backend.common.NotImplemented;

@RestController
@RequestMapping("/api/measurements")
public class MeasurementController {

    public static final String API_KEY_HEADER = "X-API-Key";

    /** The series is not sent by the sensor; it is resolved from the sensor's API key. */
    public record MeasurementRequest(@NotNull Double value, Instant timestamp) {
    }

    public record MeasurementResponse(Long id, Long seriesId, Long sensorId, Double value, Instant timestamp) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementResponse submit(@RequestHeader(API_KEY_HEADER) String apiKey,
                                      @Valid @RequestBody MeasurementRequest request) {
        throw NotImplemented.yet();
    }

    @GetMapping
    public List<MeasurementResponse> search(
            @RequestParam(name = "series", required = false) List<Long> seriesIds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        throw NotImplemented.yet();
    }

    @GetMapping("/{id}")
    public MeasurementResponse get(@PathVariable Long id) {
        throw NotImplemented.yet();
    }
}
