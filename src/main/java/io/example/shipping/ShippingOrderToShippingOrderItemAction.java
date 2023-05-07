package io.example.shipping;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.shipping.ShippingOrderEntity.CreatedShippingOrderEvent;
import io.example.shipping.ShippingOrderEntity.OrderItem;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = ShippingOrderEntity.class, ignoreUnknown = true)
public class ShippingOrderToShippingOrderItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderToShippingOrderItemAction.class);
  private final KalixClient kalixClient;

  public ShippingOrderToShippingOrderItemAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(CreatedShippingOrderEvent event) {
    log.info("Event: {}", event);
    var results = event.orderItems().stream()
        .map(orderItem -> callFor(event, orderItem))
        .toList();

    var result = CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");

    return effects().asyncReply(result);
  }

  private CompletionStage<String> callFor(CreatedShippingOrderEvent event, OrderItem orderItem) {
    var command = toCommand(event, orderItem);
    LogEvent.log("ShippingOrder", event.orderId(), "ShippingOrderItem", command.shippingOrderItemId().toEntityId(), "");
    var path = "/shipping-order-item/%s/create".formatted(command.shippingOrderItemId().toEntityId());
    var returnType = String.class;
    return kalixClient.put(path, command, returnType).execute();
  }

  private ShippingOrderItemEntity.CreateShippingOrderItemCommand toCommand(CreatedShippingOrderEvent event, ShippingOrderEntity.OrderItem orderItem) {
    return new ShippingOrderItemEntity.CreateShippingOrderItemCommand(
        ShippingOrderItemEntity.ShippingOrderItemId.of(event.orderId(), orderItem.skuId()),
        orderItem.skuName(),
        orderItem.quantity(),
        event.customerId(),
        event.orderedAt());
  }
}
