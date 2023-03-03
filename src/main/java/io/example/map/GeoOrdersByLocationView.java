package io.example.map;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import kalix.springsdk.annotations.ViewId;

@ViewId("geo-orders-by-location")
@Table("geo_orders_by_location")
@Subscribe.EventSourcedEntity(value = GeoOrderEntity.class, ignoreUnknown = true)
public class GeoOrdersByLocationView extends View<GeoOrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(GeoOrdersByLocationView.class);

  @GetMapping("/geo-orders/by-location/{topLeftLat}/{topLeftLng}/{botRightLat}/{botRightLng}")
  @Query("""
      SELECT * AS geoOrders, next_page_token() AS nextPageToken, has_more() AS hasMore
        FROM geo_orders_by_location
       WHERE position.lat <= :topLeftLat
         AND position.lng >= :topLeftLng
         AND position.lat >= :botRightLat
         AND position.lng <= :botRightLng
      OFFSET page_token_offset(:nextPageToken)
       LIMIT 1000
      """)
  public GeoOrders getGeoOrdersByLocation(
      @PathVariable Double topLeftLat,
      @PathVariable Double topLeftLng,
      @PathVariable Double botRightLat,
      @PathVariable Double botRightLng,
      @RequestParam(required = false) String nextPageToken) {
    return null;
  }

  @Override
  public GeoOrderEntity.State emptyState() {
    return GeoOrderEntity.State.empty();
  }

  public UpdateEffect<GeoOrderEntity.State> on(GeoOrderEntity.GeoOrderCreatedEvent event) {
    log.debug("State: {}\n_Event: {}", viewState(), event);
    return effects().updateState(viewState().on(event));
  }

  public UpdateEffect<GeoOrderEntity.State> on(GeoOrderEntity.GeoOrderReadyToShipEvent event) {
    log.debug("State: {}\n_Event: {}", viewState(), event);
    return effects().updateState(viewState().on(event));
  }

  public UpdateEffect<GeoOrderEntity.State> on(GeoOrderEntity.GeoOrderBackOrderedEvent event) {
    log.debug("State: {}\n_Event: {}", viewState(), event);
    return effects().updateState(viewState().on(event));
  }

  public record GeoOrders(Collection<GeoOrderEntity.State> geoOrders, String nextPageToken, boolean hasMore) {}
}
