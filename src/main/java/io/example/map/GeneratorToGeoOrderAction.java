package io.example.map;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = GeneratorEntity.class, ignoreUnknown = true)
public class GeneratorToGeoOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(GeneratorToGeoOrderAction.class);
  private final KalixClient kalixClient;

  public GeneratorToGeoOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(GeneratorEntity.GeoOrdersToGenerateEvent event) {
    log.info("Event: {}", event);
    var results = event.geoOrders().stream()
        .map(geoOrder -> {
          var path = "/geo-order/%s/create".formatted(geoOrder.geoOrderId());
          var command = new GeoOrderEntity.CreateGeoOrderCommand(geoOrder.geoOrderId(), geoOrder.position());
          var returnType = String.class;
          return kalixClient.post(path, command, returnType).execute();
        })
        .toList();

    var result = CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");

    return effects().asyncReply(result);
  }
}
