package io.example.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockOrderLotEntity.class, ignoreUnknown = true)
public class StockOrderLotToStockOrderLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderLotToStockOrderLotAction.class);
  private final ComponentClient componentClient;

  public StockOrderLotToStockOrderLotAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockOrderLotEntity.UpdatedStockOrderLotEvent event) {
    log.info("Event: {}", event);
    return callFor(event);
  }

  public Effect<String> on(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(StockOrderLotEntity.UpdatedStockOrderLotEvent event) {
    var command = new StockOrderLotEntity.ReleaseStockOrderLotCommand(event.stockOrderLotId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.stockOrderLotId().toEntityId())
            .call(StockOrderLotEntity::releaseStockOrderLot)
            .params(command));
  }

  private Effect<String> callFor(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    if (event.stockOrderLotId().lotLevel() > 0) {
      return callForStockOrderLot(event);
    } else {
      return callForStockOrder(event);
    }
  }

  private Effect<String> callForStockOrderLot(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    var upperStockOrderLotId = event.stockOrderLotId().levelUp();
    var command = new StockOrderLotEntity.UpdateSubStockOrderLotCommand(event.stockOrderLotId(), event.stockOrderLot());

    LogEvent.log("StockOrderLot", event.stockOrderLotId().toEntityId(), "StockOrderLot", upperStockOrderLotId.toEntityId(), "");

    return effects().forward(
        componentClient.forEventSourcedEntity(upperStockOrderLotId.toEntityId())
            .call(StockOrderLotEntity::updateSubStockOrderLot)
            .params(command));
  }

  private Effect<String> callForStockOrder(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    var stockOrderId = event.stockOrderLotId().stockOrderId();
    var command = new StockOrderEntity.UpdateStockOrderCommand(stockOrderId, event.stockOrderLot().quantityOrdered());

    LogEvent.log("StockOrderLot", event.stockOrderLotId().toEntityId(), "StockOrder", stockOrderId, "");

    return effects().forward(
        componentClient.forEventSourcedEntity(stockOrderId)
            .call(StockOrderEntity::update)
            .params(command));
  }
}
