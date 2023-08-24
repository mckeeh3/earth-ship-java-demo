package io.example.product;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.shipping.OrderSkuItemEntity;
import io.example.shipping.OrderSkuItemEntity.OrderSkuItemId;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = OrderSkuItemEntity.class, ignoreUnknown = true)
public class OrderSkuItemToBackOrderedLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderSkuItemToBackOrderedLotAction.class);
  private final ComponentClient componentClient;

  public OrderSkuItemToBackOrderedLotAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    log.info("Event: {}", event);
    return callFor(event.skuId(), event.orderSkuItemId(), event.orderedAt(), true);
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("Event: {}", event);
    return callFor(event.skuId(), event.orderSkuItemId(), event.orderedAt(), false);
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("Event: {}", event);
    return callFor(event.skuId(), event.orderSkuItemId(), event.orderedAt(), false);
  }

  private Effect<String> callFor(String skuId, OrderSkuItemId orderSkuItemId, Instant orderedAt, boolean backOrdered) {
    var backOrderedLotId = BackOrderedLotId.of(skuId, orderSkuItemId.toEntityId(), orderedAt);
    var upperBackOrderedLotId = backOrderedLotId.levelUp();
    var command = toCommand(backOrderedLotId, backOrdered);

    if (backOrdered) {
      LogEvent.log("OrderSkuItem", orderSkuItemId.toEntityId(), "BackOrderedLot", upperBackOrderedLotId.toEntityId(), "");
    }

    return effects().forward(
        componentClient.forEventSourcedEntity(upperBackOrderedLotId.toEntityId())
            .call(BackOrderedLotEntity::update)
            .params(command));
  }

  private BackOrderedLotEntity.UpdateSubBackOrderedLotCommand toCommand(BackOrderedLotId backOrderedLotId, boolean backOrdered) {
    var backOrderedLot = new BackOrderedLot(backOrderedLotId, backOrdered ? 1 : 0, List.of());
    return new BackOrderedLotEntity.UpdateSubBackOrderedLotCommand(backOrderedLotId, backOrderedLot);
  }
}
