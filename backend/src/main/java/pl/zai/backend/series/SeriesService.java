package pl.zai.backend.series;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zai.backend.common.ApiErrors;
import pl.zai.backend.measurement.MeasurementRepository;
import pl.zai.backend.sensor.SensorRepository;
import pl.zai.backend.series.SeriesController.SeriesInput;

@Service
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final MeasurementRepository measurementRepository;
    private final SensorRepository sensorRepository;

    public SeriesService(SeriesRepository seriesRepository, MeasurementRepository measurementRepository,
                         SensorRepository sensorRepository) {
        this.seriesRepository = seriesRepository;
        this.measurementRepository = measurementRepository;
        this.sensorRepository = sensorRepository;
    }

    @Transactional(readOnly = true)
    public List<Series> list() {
        return seriesRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Series get(Long id) {
        return seriesRepository.findById(id).orElseThrow(() -> ApiErrors.notFound("Series " + id + " not found"));
    }

    @Transactional
    public Series create(SeriesInput input) {
        validateRange(input);
        Series series = new Series();
        apply(series, input);
        return seriesRepository.save(series);
    }

    @Transactional
    public Series update(Long id, SeriesInput input) {
        Series series = get(id);
        validateRange(input);
        if (measurementRepository.countOutsideRange(id, input.minValue(), input.maxValue()) > 0) {
            throw ApiErrors.conflict("Series has measurements outside the new range [minValue, maxValue]");
        }
        apply(series, input);
        return series;
    }

    /** Removes the series together with its measurements and the sensors assigned to it. */
    @Transactional
    public void delete(Long id) {
        Series series = get(id);
        measurementRepository.deleteBySeriesId(id);
        sensorRepository.deleteBySeriesId(id);
        seriesRepository.delete(series);
    }

    private static void validateRange(SeriesInput input) {
        if (input.minValue() >= input.maxValue()) {
            throw ApiErrors.unprocessable("minValue must be less than maxValue");
        }
    }

    private static void apply(Series series, SeriesInput input) {
        series.setName(input.name());
        series.setMinValue(input.minValue());
        series.setMaxValue(input.maxValue());
        series.setColor(input.color());
        series.setIcon(input.icon());
        series.setUnit(input.unit());
    }
}
