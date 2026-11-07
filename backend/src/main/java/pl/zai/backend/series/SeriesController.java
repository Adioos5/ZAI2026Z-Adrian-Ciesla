package pl.zai.backend.series;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.zai.backend.auth.RequiresAdmin;

@RestController
@RequestMapping("/api/series")
public class SeriesController {

    public record SeriesInput(@NotBlank @Size(max = 100) String name,
                              @NotNull Double minValue,
                              @NotNull Double maxValue,
                              @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
                              String icon,
                              @Size(max = 20) String unit) {
    }

    public record SeriesResponse(Long id, String name, double minValue, double maxValue,
                                 String color, String icon, String unit) {

        static SeriesResponse from(Series series) {
            return new SeriesResponse(series.getId(), series.getName(), series.getMinValue(),
                    series.getMaxValue(), series.getColor(), series.getIcon(), series.getUnit());
        }
    }

    private final SeriesService seriesService;

    public SeriesController(SeriesService seriesService) {
        this.seriesService = seriesService;
    }

    @GetMapping
    public List<SeriesResponse> list() {
        return seriesService.list().stream().map(SeriesResponse::from).toList();
    }

    @GetMapping("/{seriesId}")
    public SeriesResponse get(@PathVariable Long seriesId) {
        return SeriesResponse.from(seriesService.get(seriesId));
    }

    @RequiresAdmin
    @PostMapping
    public ResponseEntity<SeriesResponse> create(@Valid @RequestBody SeriesInput input) {
        Series series = seriesService.create(input);
        return ResponseEntity.created(URI.create("/api/series/" + series.getId()))
                .body(SeriesResponse.from(series));
    }

    @RequiresAdmin
    @PutMapping("/{seriesId}")
    public SeriesResponse update(@PathVariable Long seriesId, @Valid @RequestBody SeriesInput input) {
        return SeriesResponse.from(seriesService.update(seriesId, input));
    }

    @RequiresAdmin
    @DeleteMapping("/{seriesId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long seriesId) {
        seriesService.delete(seriesId);
    }
}
