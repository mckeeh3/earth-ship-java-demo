package io.example.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.shipping.ShippingOrderEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = ShippingOrderEntity.class, ignoreUnknown = true)
public class ShippingOrderToOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderToOrderAction.class);
  private final KalixClient kalixClient;

  public ShippingOrderToOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(ShippingOrderEntity.ReadyToShipOrderItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(ShippingOrderEntity.ReadyToShipOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(ShippingOrderEntity.ReleasedOrderItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(ShippingOrderEntity.ReleasedOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(ShippingOrderEntity.BackOrderedOrderItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(ShippingOrderEntity.BackOrderedOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private DeferredCall<Any, String> callFor(ShippingOrderEntity.ReadyToShipOrderItemEvent event) {
    var path = "/order/%s/ready-to-ship-order-item".formatted(event.orderId());
    var command = new OrderEntity.ReadyToShipOrderItemCommand(event.orderId(), event.skuId(), event.readyToShipAt());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(ShippingOrderEntity.ReadyToShipOrderEvent event) {
    var path = "/order/%s/ready-to-ship".formatted(event.orderId());
    var command = new OrderEntity.ReadyToShipOrderCommand(event.orderId(), event.readyToShipAt());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(ShippingOrderEntity.ReleasedOrderItemEvent event) {
    var path = "/order/%s/release-order-item".formatted(event.orderId());
    var command = new OrderEntity.ReleaseOrderItemCommand(event.orderId(), event.skuId());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(ShippingOrderEntity.ReleasedOrderEvent event) {
    var path = "/order/%s/release".formatted(event.orderId());
    var command = new OrderEntity.ReleaseOrderCommand(event.orderId());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(ShippingOrderEntity.BackOrderedOrderItemEvent event) {
    var path = "/order/%s/back-order-order-item".formatted(event.orderId());
    var command = new OrderEntity.BackOrderOrderItemCommand(event.orderId(), event.skuId(), event.backOrderedAt());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(ShippingOrderEntity.BackOrderedOrderEvent event) {
    var path = "/order/%s/back-order".formatted(event.orderId());
    var command = new OrderEntity.BackOrderOrderCommand(event.orderId(), event.backOrderedAt());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }
}
