package io.example.shipping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = ShippingOrderItemEntity.class, ignoreUnknown = true)
public class ShippingOrderItemToShippingOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderItemToShippingOrderAction.class);
  private final ComponentClient componentClient;

  public ShippingOrderItemToShippingOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(ShippingOrderItemEntity.ReadyToShipOrderItemEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("ShippingOrderItem", event.shippingOrderItemId().toEntityId(), "ShippingOrder", event.shippingOrderItemId().orderId(), "color green");

    return callFor(event);
  }

  public Effect<String> on(ShippingOrderItemEntity.ReleasedOrderItemEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("ShippingOrderItem", event.shippingOrderItemId().toEntityId(), "ShippingOrder", event.shippingOrderItemId().orderId(), "color yellow");

    return callFor(event);
  }

  public Effect<String> on(ShippingOrderItemEntity.BackOrderedOrderItemEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("ShippingOrderItem", event.shippingOrderItemId().toEntityId(), "ShippingOrder", event.shippingOrderItemId().orderId(), "color red");

    return callFor(event);
  }

  private Effect<String> callFor(ShippingOrderItemEntity.ReadyToShipOrderItemEvent event) {
    var command = new ShippingOrderEntity.ReadyToShipOrderItemCommand(event.shippingOrderItemId().orderId(), event.shippingOrderItemId().skuId(), event.readyToShipAt());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.shippingOrderItemId().orderId())
            .call(ShippingOrderEntity::readyToShipOrderItem)
            .params(command));
  }

  private Effect<String> callFor(ShippingOrderItemEntity.ReleasedOrderItemEvent event) {
    var command = new ShippingOrderEntity.ReleaseOrderItemCommand(event.shippingOrderItemId().orderId(), event.shippingOrderItemId().skuId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.shippingOrderItemId().orderId())
            .call(ShippingOrderEntity::releaseOrderItem)
            .params(command));
  }

  private Effect<String> callFor(ShippingOrderItemEntity.BackOrderedOrderItemEvent event) {
    var command = new ShippingOrderEntity.BackOrderOrderItemCommand(event.shippingOrderItemId().orderId(), event.shippingOrderItemId().skuId(), event.backOrderedAt());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.shippingOrderItemId().orderId())
            .call(ShippingOrderEntity::backOrderOrderItem)
            .params(command));
  }
}
