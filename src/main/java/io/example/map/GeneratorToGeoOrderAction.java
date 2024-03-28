package io.example.map;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.map.GeneratorEntity.GeoOrder;
import io.example.map.GeoOrderEntity.CreateGeoOrderCommand;
import kalix.javasdk.HttpResponse;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = GeneratorEntity.class, ignoreUnknown = true)
public class GeneratorToGeoOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(GeneratorToGeoOrderAction.class);
  private final ComponentClient componentClient;

  public GeneratorToGeoOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<HttpResponse> on(GeneratorEntity.GeoOrdersToGenerateEvent event) {
    log.info("Event: {}", event);

    var results = event.geoOrders().stream()
        .map(geoOrder -> toCommand(event, geoOrder))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  private CreateGeoOrderCommand toCommand(GeneratorEntity.GeoOrdersToGenerateEvent event, GeoOrder geoOrder) {
    LogEvent.log("Generator", event.generatorId(), "GeoOrder", geoOrder.geoOrderId(), "");
    return new GeoOrderEntity.CreateGeoOrderCommand(geoOrder.geoOrderId(), geoOrder.position());
  }

  private CompletionStage<HttpResponse> callFor(CreateGeoOrderCommand command) {
    return componentClient.forEventSourcedEntity(command.geoOrderId())
        .call(GeoOrderEntity::create)
        .params(command)
        .execute();
  }

  private CompletableFuture<HttpResponse> waitForCallsToFinish(List<CompletionStage<HttpResponse>> results) {
    return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> HttpResponse.ok());
  }
}
