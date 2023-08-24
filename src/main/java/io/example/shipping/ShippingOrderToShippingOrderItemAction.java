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
public class ShippingOrderToShippingOrderItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderToShippingOrderItemAction.class);
  private final ComponentClient componentClient;

  public ShippingOrderToShippingOrderItemAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(ShippingOrderEntity.CreatedShippingOrderEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(ShippingOrderEntity.CreatedShippingOrderEvent event) {
    var results = event.orderItems().stream()
        .map(orderItem -> toCommand(event, orderItem))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  private CompletionStage<String> callFor(ShippingOrderItemEntity.CreateShippingOrderItemCommand command) {
    LogEvent.log("ShippingOrder", command.shippingOrderItemId().orderId(), "ShippingOrderItem", command.shippingOrderItemId().toEntityId(), "color yellow");

    return componentClient.forEventSourcedEntity(command.shippingOrderItemId().toEntityId())
        .call(ShippingOrderItemEntity::create)
        .params(command)
        .execute();
  }

  private ShippingOrderItemEntity.CreateShippingOrderItemCommand toCommand(ShippingOrderEntity.CreatedShippingOrderEvent event, ShippingOrderEntity.OrderItem orderItem) {
    return new ShippingOrderItemEntity.CreateShippingOrderItemCommand(
        ShippingOrderItemEntity.ShippingOrderItemId.of(event.orderId(), orderItem.skuId()),
        orderItem.skuName(),
        orderItem.quantity(),
        event.customerId(),
        event.orderedAt());
  }

  private CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");
  }
}
