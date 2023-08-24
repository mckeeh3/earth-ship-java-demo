package io.example.shipping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.shipping.OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent;
import io.example.shipping.OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent;
import io.example.shipping.OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = OrderSkuItemEntity.class, ignoreUnknown = true)
public class OrderSkuItemToShippingOrderItemAction extends Action {
  private static final Logger logger = LoggerFactory.getLogger(OrderSkuItemToShippingOrderItemAction.class);
  private final ComponentClient componentClient;

  public OrderSkuItemToShippingOrderItemAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    logger.info("Event: {}", event);
    LogEvent.log("OrderSkuItem", event.orderSkuItemId().toEntityId(), "ShippingOrderItem", shippingOrderItemEntityId(event), "color green");

    return callFor(event);
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    logger.info("Event: {}", event);
    LogEvent.log("OrderSkuItem", event.orderSkuItemId().toEntityId(), "ShippingOrderItem", shippingOrderItemEntityId(event), "color green");

    return callFor(event);
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    logger.info("Event: {}", event);
    LogEvent.log("OrderSkuItem", event.orderSkuItemId().toEntityId(), "ShippingOrderItem", shippingOrderItemEntityId(event), "color yellow");

    return callFor(event);
  }

  public Effect<String> on(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    logger.info("Event: {}", event);
    LogEvent.log("OrderSkuItem", event.orderSkuItemId().toEntityId(), "ShippingOrderItem", shippingOrderItemEntityId(event), "color red");

    return callFor(event);
  }

  private Effect<String> callFor(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    return effects().forward(
        componentClient.forEventSourcedEntity(shippingOrderItemEntityId(event))
            .call(ShippingOrderItemEntity::readyToShip)
            .params(toCommand(event)));
  }

  private Effect<String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    return effects().forward(
        componentClient.forEventSourcedEntity(shippingOrderItemEntityId(event))
            .call(ShippingOrderItemEntity::readyToShip)
            .params(toCommand(event)));
  }

  private Effect<String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    return effects().forward(
        componentClient.forEventSourcedEntity(shippingOrderItemEntityId(event))
            .call(ShippingOrderItemEntity::release)
            .params(toCommand(event)));
  }

  private Effect<String> callFor(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    return effects().forward(
        componentClient.forEventSourcedEntity(shippingOrderItemEntityId(event))
            .call(ShippingOrderItemEntity::backOrder)
            .params(toCommand(event)));
  }

  private static ShippingOrderItemEntity.ReadyToShipOrderSkuItemCommand toCommand(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    return new ShippingOrderItemEntity.ReadyToShipOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId(),
        event.readyToShipAt());
  }

  private static ShippingOrderItemEntity.ReadyToShipOrderSkuItemCommand toCommand(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    return new ShippingOrderItemEntity.ReadyToShipOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId(),
        event.readyToShipAt());
  }

  private static ShippingOrderItemEntity.ReleaseOrderSkuItemCommand toCommand(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    return new ShippingOrderItemEntity.ReleaseOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
  }

  private static ShippingOrderItemEntity.BackOrderOrderSkuItemCommand toCommand(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    return new ShippingOrderItemEntity.BackOrderOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.backOrderedAt());
  }

  private String shippingOrderItemEntityId(StockRequestedJoinToOrderAcceptedEvent event) {
    return new ShippingOrderItemEntity.ShippingOrderItemId(event.orderSkuItemId().orderId(), event.skuId()).toEntityId();
  }

  private String shippingOrderItemEntityId(OrderRequestedJoinToStockAcceptedEvent event) {
    return new ShippingOrderItemEntity.ShippingOrderItemId(event.orderSkuItemId().orderId(), event.skuId()).toEntityId();
  }

  private String shippingOrderItemEntityId(OrderRequestedJoinToStockReleasedEvent event) {
    return new ShippingOrderItemEntity.ShippingOrderItemId(event.orderSkuItemId().orderId(), event.skuId()).toEntityId();
  }

  private String shippingOrderItemEntityId(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    return new ShippingOrderItemEntity.ShippingOrderItemId(event.orderSkuItemId().orderId(), event.skuId()).toEntityId();
  }
}
