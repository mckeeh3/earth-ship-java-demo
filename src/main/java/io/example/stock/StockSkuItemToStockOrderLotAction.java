package io.example.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.shipping.OrderSkuItemEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockSkuItemEntity.class, ignoreUnknown = true)
public class StockSkuItemToStockOrderLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemToStockOrderLotAction.class);
  private final ComponentClient componentClient;

  public StockSkuItemToStockOrderLotAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockSkuItem", event.stockSkuItemId().toEntityId(), "StockOrderLot", event.stockSkuItemId().stockOrderLotId().levelUp().toEntityId(), "color green");
    return callFor(event.stockSkuItemId().stockOrderLotId(), true);
  }

  public Effect<String> on(StockSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockSkuItem", event.stockSkuItemId().toEntityId(), "StockOrderLot", event.stockSkuItemId().stockOrderLotId().levelUp().toEntityId(), "color green");
    return callFor(event.stockSkuItemId().stockOrderLotId(), true);
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    log.info("Event: {}", event);
    return callFor(event.stockSkuItemId().stockOrderLotId(), false);
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    log.info("Event: {}", event);
    return callFor(event.stockSkuItemId().stockOrderLotId(), false);
  }

  private Effect<String> callFor(StockOrderLotId subStockOrderLotId, boolean acceptedOrReleased) {
    var totalOrdered = acceptedOrReleased ? 1 : 0;
    var upperStockOrderLotId = subStockOrderLotId.levelUp();
    var subStockOrderLot = new StockOrderLot(subStockOrderLotId, 1, totalOrdered, List.of());
    var command = new StockOrderLotEntity.UpdateSubStockOrderLotCommand(subStockOrderLotId, subStockOrderLot);

    return effects().forward(
        componentClient.forEventSourcedEntity(upperStockOrderLotId.toEntityId())
            .call(StockOrderLotEntity::updateSubStockOrderLot)
            .params(command));
  }
}
