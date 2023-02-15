package io.example.map;

import static io.example.map.WorldMap.*;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = RegionEntity.class, ignoreUnknown = true)
public class RegionToRegionAction extends Action {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RegionToRegionAction.class);
  private final KalixClient kalixClient;

  public RegionToRegionAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(RegionEntity.UpdatedRegionEvent event) {
    log.info("Event: {}", event);
    var region = event.region();
    var regionId = regionIdFor(region);
    var path = "/region/%s/release-current-state".formatted(regionId);
    var command = new RegionEntity.ReleaseCurrentStateCommand(region);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(RegionEntity.ReleasedCurrentStateEvent event) {
    log.info("Event: {}", event);
    var subRegion = event.region();
    var region = regionAbove(subRegion);
    if (region == null) {
      return effects().reply("OK"); // there are no more regions above zoom level 1
    }
    var regionId = regionIdFor(region);
    var path = "/region/%s/update-sub-region".formatted(regionId);
    var command = new RegionEntity.UpdateSubRegionCommand(subRegion);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }
}
