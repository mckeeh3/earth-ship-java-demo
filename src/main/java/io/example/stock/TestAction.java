package io.example.stock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = ProductEntity.class, ignoreUnknown = true)
public class TestAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(TestAction.class);
  private final KalixClient kalixClient;

  public TestAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  // TODO: step 3: replace the following sample methods with necessary event handlers

  public Effect<String> on(SomeEvent1 event) {
    log.info("Event: {}", event);

    return onOneEventInToManyCommandsOut(event);
  }

  public Effect<String> on(SomeEvent2 event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  // TODO: step 2: remove unneeded or modify the following sample methods as needed

  private Effect<String> onOneEventInToManyCommandsOut(SomeEvent1 event) {
    var results = event.items().stream()
        .map(item -> toCommand(event, item))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  private SomeCommand toCommand(SomeEvent1 event, Item item) {
    return new SomeCommand(
        event.id(),
        item.id(),
        item.name());
  }

  private CompletionStage<String> callFor(SomeCommand command) {
    var path = "/path/%s/to/some/endpoint".formatted(command.id());
    var returnType = String.class;
    var deferredCall = kalixClient.post(path, command, returnType);

    return deferredCall.execute();
  }

  private CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");
  }

  private DeferredCall<Any, String> callFor(SomeEvent2 event) {
    var path = "/path/%s/to/some/endpoint".formatted(event.id());
    var command = new SomeCommand(event.id(), "item-id", "item-name");
    var returnType = String.class;

    return kalixClient.post(path, command, returnType);
  }

  // TODO: step 1: delete the following records and replace them with from/to entity events and commands

  public record SomeEvent1(String id, List<Item> items) {}

  public record Item(String id, String name) {}

  public record SomeCommand(String id, String itemId, String itemName) {}

  public record SomeEvent2(String id) {}
}
