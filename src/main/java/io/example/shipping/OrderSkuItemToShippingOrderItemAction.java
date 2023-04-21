package io.example.shipping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = OrderSkuItemEntity.class, ignoreUnknown = true)
public class OrderSkuItemToShippingOrderItemAction extends Action {
  private static final Logger logger = LoggerFactory.getLogger(OrderSkuItemToShippingOrderItemAction.class);
  private final KalixClient kalixClient;

  public OrderSkuItemToShippingOrderItemAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    logger.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    logger.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    logger.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    logger.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    var path = "/shipping-order-item/%s/ready-to-ship".formatted(event.orderSkuItemId().orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private static ShippingOrderItemEntity.ReadyToShipOrderSkuItemCommand toCommand(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    return new ShippingOrderItemEntity.ReadyToShipOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId(),
        event.readyToShipAt());
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    var path = "/shipping-order-item/%s/ready-to-ship".formatted(event.orderSkuItemId().orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private static ShippingOrderItemEntity.ReadyToShipOrderSkuItemCommand toCommand(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    return new ShippingOrderItemEntity.ReadyToShipOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId(),
        event.readyToShipAt());
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    var path = "/shipping-order-item/%s/release".formatted(event.orderSkuItemId().orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private static ShippingOrderItemEntity.ReleaseOrderSkuItemCommand toCommand(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    return new ShippingOrderItemEntity.ReleaseOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    var path = "/shipping-order-item/%s/back-order".formatted(event.orderSkuItemId().orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private static ShippingOrderItemEntity.BackOrderOrderSkuItemCommand toCommand(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    return new ShippingOrderItemEntity.BackOrderOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.backOrderedAt());
  }
}
