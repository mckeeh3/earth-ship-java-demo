package io.example.map;

import java.util.concurrent.CompletableFuture;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = GeneratorEntity.class, ignoreUnknown = true)
public class GeneratorToDeviceAction extends Action {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeneratorToDeviceAction.class);
  private final KalixClient kalixClient;

  public GeneratorToDeviceAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(GeneratorEntity.DevicesToGenerateEvent event) {
    log.info("Event: {}", event);
    var results = event.devices().stream()
        .map(device -> {
          var path = "/device/%s/create".formatted(device.deviceId());
          var command = new DeviceEntity.CreateDeviceCommand(device.deviceId(), device.position());
          var returnType = String.class;
          return kalixClient.post(path, command, returnType).execute();
        })
        .toList();

    var result = CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");

    return effects().asyncReply(result);
  }
}
