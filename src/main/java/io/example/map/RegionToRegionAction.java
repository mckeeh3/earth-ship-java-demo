package io.example.map;

import static io.example.map.WorldMap.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = RegionEntity.class, ignoreUnknown = true)
public class RegionToRegionAction extends Action {
  private static Logger log = LoggerFactory.getLogger(RegionToRegionAction.class);
  private final ComponentClient componentClient;

  public RegionToRegionAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(RegionEntity.UpdatedRegionEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(RegionEntity.ReleasedCurrentStateEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(RegionEntity.UpdatedRegionEvent event) {
    var region = event.region();
    var regionId = regionIdFor(region);
    var command = new RegionEntity.ReleaseCurrentStateCommand(region);

    return effects().forward(
        componentClient.forEventSourcedEntity(regionId)
            .call(RegionEntity::releaseCurrentState)
            .params(command));
  }

  private Effect<String> callFor(RegionEntity.ReleasedCurrentStateEvent event) {
    var subRegion = event.region();
    var region = regionAbove(subRegion);
    if (region == null) {
      return effects().reply("OK"); // there are no more regions above zoom level 1
    }

    var message = "color %s".formatted(event.region().geoOrderAlarmCount() > 0 ? "red" : "green");
    LogEvent.log("Region", regionIdFor(subRegion), "Region", regionIdFor(region), message);

    var regionId = regionIdFor(region);
    var command = new RegionEntity.UpdateSubRegionCommand(subRegion);

    return effects().forward(
        componentClient.forEventSourcedEntity(regionId)
            .call(RegionEntity::updateSubRegion)
            .params(command));
  }
}
