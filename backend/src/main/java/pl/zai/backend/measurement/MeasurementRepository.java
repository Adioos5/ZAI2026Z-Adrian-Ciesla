package pl.zai.backend.measurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MeasurementRepository extends JpaRepository<Measurement, Long>, JpaSpecificationExecutor<Measurement> {

    @Query("select count(m) from Measurement m where m.seriesId = :seriesId and (m.value < :min or m.value > :max)")
    long countOutsideRange(Long seriesId, double min, double max);

    @Modifying
    @Query("delete from Measurement m where m.seriesId = :seriesId")
    void deleteBySeriesId(Long seriesId);
}
