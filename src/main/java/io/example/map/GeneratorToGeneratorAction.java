package io.example.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

@Subscribe.EventSourcedEntity(value = GeneratorEntity.class, ignoreUnknown = true)
public class GeneratorToGeneratorAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(GeneratorToGeneratorAction.class);
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
