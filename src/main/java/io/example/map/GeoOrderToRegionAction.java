package io.example.map;

import static io.example.map.WorldMap.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = GeoOrderEntity.class, ignoreUnknown = true)
public class GeoOrderToRegionAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(GeoOrderToRegionAction.class);
  private final KalixClient kalixClient;

  public GeoOrderToRegionAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderCreatedEvent event) {
    log.info("Event: {}", event);
    return updateSubRegion(event.position(), false);
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderReadyToShipEvent event) {
    log.info("Event: {}", event);
    return updateSubRegion(event.position(), false);
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderBackOrderedEvent event) {
    log.info("Event: {}", event);
    return updateSubRegion(event.position(), true);
  }

  private Effect<String> updateSubRegion(LatLng position, boolean alarmOn) {
    var subRegion = new Region(zoomMax + 1, position, position, 1, alarmOn ? 1 : 0);
    var region = regionAbove(subRegion);
    var regionId = regionIdFor(region);

    var path = "/region/%s/update-sub-region".formatted(regionId);
    var command = new RegionEntity.UpdateSubRegionCommand(subRegion);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }
}
