package io.example.map;

import static io.example.map.WorldMap.*;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = DeviceEntity.class, ignoreUnknown = true)
public class DeviceToRegionAction extends Action {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeviceToRegionAction.class);
  private final KalixClient kalixClient;

  public DeviceToRegionAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(DeviceEntity.DeviceCreatedEvent event) {
    log.info("Event: {}", event);
    var subRegion = new Region(zoomMax + 1, event.position(), event.position(), 1, 0);
    var region = regionAbove(subRegion);
    var regionId = regionIdFor(region);
    var path = "/region/%s/update-sub-region".formatted(regionId);
    var command = new RegionEntity.UpdateSubRegionCommand(subRegion);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(DeviceEntity.AlarmChangedEvent event) {
    log.info("Event: {}", event);
    var subRegion = new Region(zoomMax + 1, event.position(), event.position(), 1, event.alarmOn() ? 1 : 0);
    var region = regionAbove(subRegion);
    var regionId = regionIdFor(region);
    var path = "/region/%s/update-sub-region".formatted(regionId);
    var command = new RegionEntity.UpdateSubRegionCommand(subRegion);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }
}
