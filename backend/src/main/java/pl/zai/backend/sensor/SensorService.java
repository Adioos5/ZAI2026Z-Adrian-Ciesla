package pl.zai.backend.sensor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zai.backend.common.ApiErrors;
import pl.zai.backend.common.Secrets;
import pl.zai.backend.series.SeriesRepository;

@Service
public class SensorService {

    public record Registration(Sensor sensor, String apiKey) {
    }

    private final SensorRepository sensorRepository;
    private final SeriesRepository seriesRepository;

    public SensorService(SensorRepository sensorRepository, SeriesRepository seriesRepository) {
        this.sensorRepository = sensorRepository;
        this.seriesRepository = seriesRepository;
    }

    @Transactional(readOnly = true)
    public List<Sensor> list() {
        return sensorRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Sensor get(Long id) {
        return sensorRepository.findById(id).orElseThrow(() -> ApiErrors.notFound("Sensor " + id + " not found"));
    }

    @Transactional
    public Registration register(String name, Long seriesId) {
        if (!seriesRepository.existsById(seriesId)) {
            throw ApiErrors.unprocessable("Series " + seriesId + " does not exist");
        }
        String apiKey = Secrets.randomToken();
        Sensor sensor = sensorRepository.save(new Sensor(name, seriesId, Secrets.sha256(apiKey), Instant.now()));
        return new Registration(sensor, apiKey);
    }

    /** Measurements already sent by the sensor are kept; only its key stops working. */
    @Transactional
    public void unregister(Long id) {
        sensorRepository.delete(get(id));
    }

    public Optional<Sensor> findByApiKey(String apiKey) {
        return sensorRepository.findByApiKeyHash(Secrets.sha256(apiKey));
    }
}
