package io.example.shipping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.LogEvent;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.spring.KalixClient;

@Subscribe.EventSourcedEntity(value = ShippingOrderItemEntity.class, ignoreUnknown = true)
public class ShippingOrderItemToShippingOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderItemToShippingOrderAction.class);
  private final KalixClient kalixClient;

  public ShippingOrderItemToShippingOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(ShippingOrderItemEntity.ReadyToShipOrderItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(ShippingOrderItemEntity.ReleasedOrderItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(ShippingOrderItemEntity.BackOrderedOrderItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private DeferredCall<Any, String> callFor(ShippingOrderItemEntity.ReadyToShipOrderItemEvent event) {
    var path = "/shipping-order/%s/ready-to-ship-order-item".formatted(event.shippingOrderItemId().orderId());
    var command = new ShippingOrderEntity.ReadyToShipOrderItemCommand(event.shippingOrderItemId().orderId(), event.shippingOrderItemId().skuId(), event.readyToShipAt());
    var returnType = String.class;

    LogEvent.log("ShippingOrderItem", event.shippingOrderItemId().toEntityId(), "ShippingOrder", event.shippingOrderItemId().orderId(), "color green");

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(ShippingOrderItemEntity.ReleasedOrderItemEvent event) {
    var path = "/shipping-order/%s/release-order-item".formatted(event.shippingOrderItemId().orderId());
    var command = new ShippingOrderEntity.ReleaseOrderItemCommand(event.shippingOrderItemId().orderId(), event.shippingOrderItemId().skuId());
    var returnType = String.class;

    LogEvent.log("ShippingOrderItem", event.shippingOrderItemId().toEntityId(), "ShippingOrder", event.shippingOrderItemId().orderId(), "color yellow");

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(ShippingOrderItemEntity.BackOrderedOrderItemEvent event) {
    var path = "/shipping-order/%s/back-order-order-item".formatted(event.shippingOrderItemId().orderId());
    var command = new ShippingOrderEntity.BackOrderOrderItemCommand(event.shippingOrderItemId().orderId(), event.shippingOrderItemId().skuId(), event.backOrderedAt());
    var returnType = String.class;

    LogEvent.log("ShippingOrderItem", event.shippingOrderItemId().toEntityId(), "ShippingOrder", event.shippingOrderItemId().orderId(), "color red");

    return kalixClient.put(path, command, returnType);
  }
}
