package pl.zai.backend.sensor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.zai.backend.auth.RequiresAdmin;

@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    public record SensorInput(@NotBlank @Size(max = 100) String name, @NotNull Long seriesId) {
    }

    public record SensorResponse(Long id, String name, Long seriesId, Instant createdAt, Instant lastMeasurementAt) {

        static SensorResponse from(Sensor sensor) {
            return new SensorResponse(sensor.getId(), sensor.getName(), sensor.getSeriesId(),
                    sensor.getCreatedAt(), sensor.getLastMeasurementAt());
        }
    }

    /** Returned only once, on registration; the API key is not retrievable afterwards. */
    public record SensorCreated(Long id, String name, Long seriesId, Instant createdAt, Instant lastMeasurementAt,
                                String apiKey) {
    }

    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @RequiresAdmin
    @GetMapping
    public List<SensorResponse> list() {
        return sensorService.list().stream().map(SensorResponse::from).toList();
    }

    @RequiresAdmin
    @GetMapping("/{sensorId}")
    public SensorResponse get(@PathVariable Long sensorId) {
        return SensorResponse.from(sensorService.get(sensorId));
    }

    @RequiresAdmin
    @PostMapping
    public ResponseEntity<SensorCreated> register(@Valid @RequestBody SensorInput input) {
        SensorService.Registration registration = sensorService.register(input.name(), input.seriesId());
        Sensor sensor = registration.sensor();
        SensorCreated body = new SensorCreated(sensor.getId(), sensor.getName(), sensor.getSeriesId(),
                sensor.getCreatedAt(), sensor.getLastMeasurementAt(), registration.apiKey());
        return ResponseEntity.created(URI.create("/api/sensors/" + sensor.getId())).body(body);
    }

    @RequiresAdmin
    @DeleteMapping("/{sensorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable Long sensorId) {
        sensorService.unregister(sensorId);
    }
}
