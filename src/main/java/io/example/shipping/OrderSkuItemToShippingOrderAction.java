package io.example.shipping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    var path = "/shipping-order/%s/ready-to-ship-order-sku-item".formatted(event.orderSkuItemId().orderId());
    var command = new ShippingOrderEntity.ReadyToShipOrderSkuItemCommand(
        event.orderSkuItemId().orderId(),
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId(),
        event.readyToShipAt());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    logger.info("Event: {}", event);

    var path = "/shipping-order/%s/ready-to-ship-order-sku-item".formatted(event.orderSkuItemId().orderId());
    var command = new ShippingOrderEntity.ReadyToShipOrderSkuItemCommand(
        event.orderSkuItemId().orderId(),
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId(),
        event.readyToShipAt());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    logger.info("Event: {}", event);

    var path = "/shipping-order/%s/release-order-sku-item".formatted(event.orderSkuItemId().orderId());
    var command = new ShippingOrderEntity.ReleaseOrderSkuItemCommand(
        event.orderSkuItemId().orderId(),
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }
}
