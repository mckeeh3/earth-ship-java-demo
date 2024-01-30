package io.example.shipping;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = ShippingOrderEntity.class, ignoreUnknown = true)
public class ShippingOrderToOrderItemRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderToOrderItemRedTreeAction.class);
  private final ComponentClient componentClient;

  public ShippingOrderToOrderItemRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(ShippingOrderEntity.ShippingOrderCreatedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  Effect<String> callFor(ShippingOrderEntity.ShippingOrderCreatedEvent event) {
    var results = event.orderItems().stream()
        .map(orderItem -> toCommand(event, orderItem))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  CompletionStage<String> callFor(OrderItemRedTreeEntity.OrderItemCreateCommand command) {
    LogEvent.log("ShippingOrder", command.orderItemRedTreeId().orderId(), "OrderItemRedTree", command.orderItemRedTreeId().toEntityId(), "color yellow");

    return componentClient.forEventSourcedEntity(command.orderItemRedTreeId().toEntityId())
        .call(OrderItemRedTreeEntity::orderItemCreate)
        .params(command)
        .execute();
  }

  OrderItemRedTreeEntity.OrderItemCreateCommand toCommand(ShippingOrderEntity.ShippingOrderCreatedEvent event, ShippingOrderEntity.OrderItem orderItem) {
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of(event.orderId(), orderItem.skuId());

    return new OrderItemRedTreeEntity.OrderItemCreateCommand(
        orderItemRedTreeId,
        null, // this is the tree trunk, so it has no parent
        orderItem.quantity());
  }

  CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");
  }
}