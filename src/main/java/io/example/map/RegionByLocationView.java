package io.example.map;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import io.example.map.WorldMap.Region;
import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;

@ViewId("regions-by-location-v1")
@Table("regions_by_location")
@Subscribe.EventSourcedEntity(value = RegionEntity.class, ignoreUnknown = true)
public class RegionByLocationView extends View<RegionByLocationView.RegionViewRow> {
  private static final Logger log = LoggerFactory.getLogger(RegionByLocationView.class);

  // x-min = topLeft.lng
  // x-max = botRight.lng
  // y-min = botRight.lat
  // y-max = topLeft.lat
  //
  // isOverLapping1d((y-max1, x-min1)), (y-min1, x-max1)), (y-max2, x-min2)), (y-min2, x-max2)) =
  // __y-max1 >= y-min2 && y-min1 <= y-max2 && x-max1 >= x-min2 && x-min1 <= x-max2
  // _____y-max1 >= y-min2_______&&______y-min1 <= y-max2_______&&_______x-max1 >= x-min2______&&_______x-min1 <= x-max2
  // topLeft.lat >= botRight.lat && topLeft.lng <= botRight.lng && botRight.lat <= topLeft.lat && botRight.lng >= topLeft.lng

  @GetMapping("/regions/by-location/{zoom}/{topLeftLat}/{topLeftLng}/{botRightLat}/{botRightLng}")
  @Query("""
      SELECT * AS regions FROM regions_by_location
       LIMIT 1000
       WHERE region.zoom = :zoom
         AND region.topLeft.lat >= :botRightLat
         AND region.topLeft.lng <= :botRightLng
         AND region.botRight.lat <= :topLeftLat
         AND region.botRight.lng >= :topLeftLng
         AND geoOrderCount > 0
      """)
  public Regions getRegionsByLocation(@PathVariable Integer zoom, @PathVariable Double topLeftLat, @PathVariable Double topLeftLng, @PathVariable Double botRightLat, @PathVariable Double botRightLng) {
    return null;
  }

  public UpdateEffect<RegionViewRow> on(RegionEntity.ReleasedCurrentStateEvent event) {
    log.debug("State: {}\n_Event: {}", viewState(), event);
    return effects().updateState(RegionViewRow.on(event));
  }

  public record RegionViewRow(Region region, int geoOrderCount, int geoOrderAlarmCount) {

    static RegionViewRow on(RegionEntity.ReleasedCurrentStateEvent event) {
      return new RegionViewRow(event.region(), event.region().geoOrderCount(), event.region().geoOrderAlarmCount());
    }
  }

  public record Regions(Collection<RegionViewRow> regions) {}
}
