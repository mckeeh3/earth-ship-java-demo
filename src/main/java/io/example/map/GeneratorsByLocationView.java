package io.example.map;

import java.util.Collection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import io.example.map.WorldMap.LatLng;
import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import kalix.springsdk.annotations.ViewId;

@ViewId("generators-by-location")
@Table("generators_by_location")
@Subscribe.EventSourcedEntity(value = GeneratorEntity.class, ignoreUnknown = true)
public class GeneratorsByLocationView extends View<GeneratorsByLocationView.GeneratorViewRow> {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeneratorsByLocationView.class);

  @GetMapping("/generators/by-location/{topLeftLat}/{topLeftLng}/{botRightLat}/{botRightLng}")
  @Query("""
      SELECT * AS generators FROM generators_by_location
       LIMIT 1000
       WHERE position.lat <= :topLeftLat
         AND position.lng >= :topLeftLng
         AND position.lat >= :botRightLat
         AND position.lng <= :botRightLng
      """)
  public Generators getGeneratorsByLocation(@PathVariable Double topLeftLat, @PathVariable Double topLeftLng, @PathVariable Double botRightLat, @PathVariable Double botRightLng) {
    return null;
  }

  public UpdateEffect<GeneratorViewRow> on(GeneratorEntity.GeneratorCreatedEvent event) {
    log.debug("State: {}\nEvent: {}", viewState(), event);
    return effects().updateState(new GeneratorViewRow(
        event.generatorId(),
        event.position(),
        event.radiusKm(),
        event.ratePerSecond(),
        event.startTimeMs(),
        event.deviceCountLimit(),
        0));
  }

  public UpdateEffect<GeneratorViewRow> on(GeneratorEntity.GeneratedEvent event) {
    log.debug("State: {}\nEvent: {}", viewState(), event);
    return effects().updateState(viewState().on(event));
  }

  public record GeneratorViewRow(
      String generatorId,
      LatLng position,
      double radiusKm,
      int ratePerSecond,
      long startTimeMs,
      int deviceCountLimit,
      int deviceCountCurrent) {

    GeneratorViewRow on(GeneratorEntity.GeneratedEvent event) {
      return new GeneratorViewRow(
          generatorId,
          position,
          radiusKm,
          ratePerSecond,
          startTimeMs,
          deviceCountLimit,
          event.deviceCountCurrent());
    }
  }

  public record Generators(Collection<GeneratorViewRow> generators) {}
}
