package io.example.map;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = GeneratorEntity.class, ignoreUnknown = true)
public class GeneratorToGeneratorAction extends Action {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeneratorToGeneratorAction.class);
  private final KalixClient kalixClient;

  public GeneratorToGeneratorAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(GeneratorEntity.GeneratorCreatedEvent event) {
    log.info("Event: {}", event);
    var path = "/generator/%s/generate".formatted(event.generatorId());
    var command = new GeneratorEntity.GenerateCommand(event.generatorId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(GeneratorEntity.GeneratedEvent event) {
    log.info("Event: {}", event);
    var path = "/generator/%s/generate".formatted(event.generatorId());
    var command = new GeneratorEntity.GenerateCommand(event.generatorId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }
}
