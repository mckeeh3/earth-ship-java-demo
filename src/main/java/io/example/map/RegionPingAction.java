package io.example.map;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static io.example.map.WorldMap.*;

import akka.Done;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;

@RequestMapping("/region-ping")
public class RegionPingAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(RegionPingAction.class);
  private final KalixClient kalixClient;

  public RegionPingAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @PostMapping("/{regionId}/create-timer")
  public Effect<String> createTimer(@PathVariable String regionId) {
    log.info("Create timer, regionId: {}", regionId);

    var path = "/region-ping/%s/ping-region".formatted(regionId);
    var returnType = String.class;
    var deferredCall = kalixClient.post(path, returnType);

    var timer = startTimer(regionId);

    return effects().asyncReply(
        timer
            .thenCompose(done -> deferredCall.execute())
            .thenApply(__ -> "OK"));
  }

  @PostMapping("/{regionId}/ping-region")
  public Effect<String> pingRegion(@PathVariable String regionId) {
    log.debug("Ping region, regionId: {}", regionId);

    return effects().asyncReply(
        getRegion(regionId)
            .thenCompose(regionEntity -> pingSubRegions(regionEntity))
            .thenApply(__ -> "OK"));
  }

  private CompletionStage<Done> startTimer(String regionId) {
    var timeNow = Instant.now();
    var timerId = "%s-%d".formatted(regionId, timeNow.getEpochSecond());
    var secondsToNextMinute = 60 - timeNow.getEpochSecond() % 60;
    var timerDuration = Duration.ofSeconds(secondsToNextMinute == 0 ? 60 : secondsToNextMinute);
    var path = "/region-ping/%s/create-timer".formatted(regionId);
    var returnType = String.class;
    var deferredCall = kalixClient.post(path, returnType);

    return timers().startSingleTimer(timerId, timerDuration, deferredCall);
  }

  private CompletionStage<RegionEntity.State> getRegion(String regionId) {
    var path = "/region/%s".formatted(regionId);
    var returnType = RegionEntity.State.class;
    var deferredCall = kalixClient.get(path, returnType);

    return deferredCall.execute();
  }

  private CompletionStage<String> pingSubRegions(RegionEntity.State regionEntity) {
    regionEntity.subRegions().forEach(subRegion -> pingSubRegion(subRegion));

    return CompletableFuture.completedStage("OK");
  }

  private void pingSubRegion(Region subRegion) {
    if (subRegion.zoom() > WorldMap.zoomMax) {
      var geoOrderId = GeoOrderEntity.geoOrderIdFor(subRegion.topLeft());
      var path = "/geo-order/%s/ping".formatted(geoOrderId);
      var returnType = String.class;

      kalixClient.put(path, returnType).execute();
    } else {
      var regionId = regionIdFor(subRegion);
      var path = "/region-ping/%s/ping-region".formatted(regionId);
      var returnType = String.class;

      kalixClient.post(path, returnType).execute();
    }
  }
}
