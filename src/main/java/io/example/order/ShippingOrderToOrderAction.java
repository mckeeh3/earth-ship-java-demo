package io.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.shipping.ShippingOrderEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = ShippingOrderEntity.class, ignoreUnknown = true)
public class ShippingOrderToOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderToOrderAction.class);
  private final ComponentClient componentClient;

  public ShippingOrderToOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(ShippingOrderEntity.OrderItemUpdatedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(ShippingOrderEntity.OrderItemUpdatedEvent event) {
    var orderItem = event.orderItems().stream()
        .filter(i -> i.skuId().equals(event.skuId()))
        .findFirst()
        .orElse(new ShippingOrderEntity.OrderItem(event.skuId(), null, 0, null, null));
    var command = new OrderEntity.OrderItemUpdateCommand(event.orderId(), event.skuId(), orderItem.readyToShipAt(), orderItem.backOrderedAt());

    var statusColor = event.orderBackOrderedAt() != null
        ? "color red"
        : event.orderReadyToShipAt() != null
            ? "color green"
            : "color yellow";
    LogEvent.log("ShippingOrder", event.orderId(), "Order", event.orderId(), statusColor);

    return effects().forward(
        componentClient.forEventSourcedEntity(event.orderId())
            .call(OrderEntity::orderItemUpdate)
            .params(command));
  }
}
