package io.example.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = StockOrderLotEntity.class, ignoreUnknown = true)
public class StockOrderLotToStockOrderLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderLotToStockOrderLotAction.class);
  private final KalixClient kalixClient;

  public StockOrderLotToStockOrderLotAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(StockOrderLotEntity.UpdatedStockOrderLotEvent event) {
    log.info("Event: {}", event);
    var path = "/stockOrderLot/%s/releaseStockOrderLot".formatted(event.stockOrderLotId().toEntityId());
    var command = new StockOrderLotEntity.ReleaseStockOrderLotCommand(event.stockOrderLotId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    log.info("Event: {}", event);

    if (event.stockOrderLotId().lotLevel() > 0) {
      return updateUpperStockOrderLot(event);
    } else {
      return updateStockOrder(event);
    }
  }

  private Effect<String> updateUpperStockOrderLot(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    var stockOrderLotId = event.stockOrderLotId().levelUp();
    var path = "/stockOrderLot/%s/updateSubStockOrderLot".formatted(stockOrderLotId.toEntityId());
    var command = new StockOrderLotEntity.UpdateSubStockOrderLotCommand(event.stockOrderLotId(), event.stockOrderLot());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  private Effect<String> updateStockOrder(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    var stockOrderId = event.stockOrderLotId().stockOrderId();
    var path = "/stockOrder/%s/update".formatted(stockOrderId);
    var command = new StockOrderEntity.UpdateStockOrderCommand(stockOrderId, event.stockOrderLot().quantityTotal(), event.stockOrderLot().quantityOrdered());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }
}
