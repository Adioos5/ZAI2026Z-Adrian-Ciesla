package pl.zai.backend.series;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.zai.backend.common.NotImplemented;

@RestController
@RequestMapping("/api/series")
public class SeriesController {

    public record SeriesRequest(@NotBlank String name, String description, String unit,
                                Double minValue, Double maxValue) {
    }

    public record SeriesResponse(Long id, String name, String description, String unit,
                                 Double minValue, Double maxValue) {
    }

    @GetMapping
    public List<SeriesResponse> list() {
        throw NotImplemented.yet();
    }

    @GetMapping("/{id}")
    public SeriesResponse get(@PathVariable Long id) {
        throw NotImplemented.yet();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeriesResponse create(@Valid @RequestBody SeriesRequest request) {
        throw NotImplemented.yet();
    }

    @PutMapping("/{id}")
    public SeriesResponse update(@PathVariable Long id, @Valid @RequestBody SeriesRequest request) {
        throw NotImplemented.yet();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        throw NotImplemented.yet();
    }
}
