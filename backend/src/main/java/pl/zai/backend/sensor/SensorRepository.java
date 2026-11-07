package pl.zai.backend.sensor;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SensorRepository extends JpaRepository<Sensor, Long> {

    Optional<Sensor> findByApiKeyHash(String apiKeyHash);

    @Modifying
    @Query("delete from Sensor s where s.seriesId = :seriesId")
    void deleteBySeriesId(Long seriesId);
}
