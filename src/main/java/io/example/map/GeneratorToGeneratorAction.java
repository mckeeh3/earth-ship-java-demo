package io.example.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = GeneratorEntity.class, ignoreUnknown = true)
public class GeneratorToGeneratorAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(GeneratorToGeneratorAction.class);
  private final ComponentClient componentClient;

  public GeneratorToGeneratorAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(GeneratorEntity.GeneratorCreatedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(GeneratorEntity.GeneratedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(GeneratorEntity.GeneratorCreatedEvent event) {
    var command = new GeneratorEntity.GenerateCommand(event.generatorId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.generatorId())
            .call(GeneratorEntity::generate)
            .params(command));
  }

  private Effect<String> callFor(GeneratorEntity.GeneratedEvent event) {
    var command = new GeneratorEntity.GenerateCommand(event.generatorId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.generatorId())
            .call(GeneratorEntity::generate)
            .params(command));
  }
}
