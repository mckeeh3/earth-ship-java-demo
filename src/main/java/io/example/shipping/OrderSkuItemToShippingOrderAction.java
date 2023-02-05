package io.example.shipping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = OrderSkuItemEntity.class, ignoreUnknown = true)
public class OrderSkuItemToShippingOrderAction extends Action {
  private static final Logger logger = LoggerFactory.getLogger(OrderSkuItemToShippingOrderAction.class);
  private final KalixClient kalixClient;

  public OrderSkuItemToShippingOrderAction(KalixClient kalixClient) {
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
    var path = "/shipping-order/%s/ready-to-ship-order-sku-item".formatted(event.orderSkuItemId().orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private static ShippingOrderEntity.ReadyToShipOrderSkuItemCommand toCommand(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    return new ShippingOrderEntity.ReadyToShipOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId(),
        event.readyToShipAt());
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    var path = "/shipping-order/%s/ready-to-ship-order-sku-item".formatted(event.orderSkuItemId().orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private static ShippingOrderEntity.ReadyToShipOrderSkuItemCommand toCommand(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    return new ShippingOrderEntity.ReadyToShipOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId(),
        event.readyToShipAt());
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    var path = "/shipping-order/%s/release-order-sku-item".formatted(event.orderSkuItemId().orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private static ShippingOrderEntity.ReleaseOrderSkuItemCommand toCommand(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    return new ShippingOrderEntity.ReleaseOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    var path = "/shipping-order/%s/back-order-order-sku-item".formatted(event.orderSkuItemId().orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private static ShippingOrderEntity.BackOrderOrderSkuItemCommand toCommand(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    return new ShippingOrderEntity.BackOrderOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.backOrderedAt());
  }
}
