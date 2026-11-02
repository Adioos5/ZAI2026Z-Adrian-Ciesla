package pl.zai.backend.sensor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.zai.backend.common.NotImplemented;

@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    /** A sensor is bound to the series its measurements are stored in. */
    public record SensorRequest(@NotBlank String name, @NotNull Long seriesId) {
    }

    public record SensorResponse(Long id, String name, Long seriesId) {
    }

    /** Returned only once, on registration; the API key is not retrievable afterwards. */
    public record SensorRegistrationResponse(Long id, String name, Long seriesId, String apiKey) {
    }

    @GetMapping
    public List<SensorResponse> list() {
        throw NotImplemented.yet();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SensorRegistrationResponse register(@Valid @RequestBody SensorRequest request) {
        throw NotImplemented.yet();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable Long id) {
        throw NotImplemented.yet();
    }
}
