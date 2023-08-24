package io.example.map;

import static io.example.map.WorldMap.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = GeoOrderEntity.class, ignoreUnknown = true)
public class GeoOrderToRegionAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(GeoOrderToRegionAction.class);
  private final ComponentClient componentClient;

  public GeoOrderToRegionAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderCreatedEvent event) {
    log.info("Event: {}", event);

    return callFor(event.geoOrderId(), event.position(), false, "color yellow");
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderReadyToShipEvent event) {
    log.info("Event: {}", event);

    return callFor(event.geoOrderId(), event.position(), false, "color green");
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderBackOrderedEvent event) {
    log.info("Event: {}", event);

    return callFor(event.geoOrderId(), event.position(), true, "color red");
  }

  private Effect<String> callFor(String geoOrderId, LatLng position, boolean alarmOn, String message) {
    var subRegion = new Region(zoomMax + 1, position, position, 1, alarmOn ? 1 : 0);
    var region = regionAbove(subRegion);
    var regionId = regionIdFor(region);

    LogEvent.log("GeoOrder", geoOrderId, "Region", regionId, message);

    var command = new RegionEntity.UpdateSubRegionCommand(subRegion);
    return effects().forward(componentClient.forEventSourcedEntity(regionId)
        .call(RegionEntity::updateSubRegion)
        .params(command));
  }
}
