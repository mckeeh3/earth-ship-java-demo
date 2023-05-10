package io.example.map;

import static io.example.map.WorldMap.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = GeoOrderEntity.class, ignoreUnknown = true)
public class GeoOrderToRegionAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(GeoOrderToRegionAction.class);
  private final KalixClient kalixClient;

  public GeoOrderToRegionAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderCreatedEvent event) {
    log.info("Event: {}", event);
    return updateSubRegion(event.geoOrderId(), event.position(), false, "color yellow");
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderReadyToShipEvent event) {
    log.info("Event: {}", event);
    return updateSubRegion(event.geoOrderId(), event.position(), false, "color green");
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderBackOrderedEvent event) {
    log.info("Event: {}", event);
    return updateSubRegion(event.geoOrderId(), event.position(), true, "color red");
  }

  private Effect<String> updateSubRegion(String geoOrderId, LatLng position, boolean alarmOn, String message) {
    var subRegion = new Region(zoomMax + 1, position, position, 1, alarmOn ? 1 : 0);
    var region = regionAbove(subRegion);
    var regionId = regionIdFor(region);

    LogEvent.log("GeoOrder", geoOrderId, "Region", regionId, message);

    var path = "/region/%s/update-sub-region".formatted(regionId);
    var command = new RegionEntity.UpdateSubRegionCommand(subRegion);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }
}
