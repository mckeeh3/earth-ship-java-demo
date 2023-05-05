package io.example.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.LogEvent;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = StockOrderLotEntity.class, ignoreUnknown = true)
public class StockOrderLotToStockOrderLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderLotToStockOrderLotAction.class);
  private final KalixClient kalixClient;

  public StockOrderLotToStockOrderLotAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(StockOrderLotEntity.UpdatedStockOrderLotEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    log.info("Event: {}", event);

    if (event.stockOrderLotId().lotLevel() > 0) {
      return effects().forward(callForStockOrderLot(event));
    } else {
      return effects().forward(callForStockOrder(event));
    }
  }

  private DeferredCall<Any, String> callFor(StockOrderLotEntity.UpdatedStockOrderLotEvent event) {
    var path = "/stock-order-lot/%s/release".formatted(event.stockOrderLotId().toEntityId());
    var command = new StockOrderLotEntity.ReleaseStockOrderLotCommand(event.stockOrderLotId());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callForStockOrderLot(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    var upperStockOrderLotId = event.stockOrderLotId().levelUp();
    var path = "/stock-order-lot/%s/update".formatted(upperStockOrderLotId.toEntityId());
    var command = new StockOrderLotEntity.UpdateSubStockOrderLotCommand(event.stockOrderLotId(), event.stockOrderLot());
    var returnType = String.class;

    LogEvent.log("StockOrderLot", event.stockOrderLotId().toEntityId(), "StockOrderLot", upperStockOrderLotId.toEntityId());

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callForStockOrder(StockOrderLotEntity.ReleasedStockOrderLotEvent event) {
    var stockOrderId = event.stockOrderLotId().stockOrderId();
    var path = "/stock-order/%s/update".formatted(stockOrderId);
    var command = new StockOrderEntity.UpdateStockOrderCommand(stockOrderId, event.stockOrderLot().quantityTotal(), event.stockOrderLot().quantityOrdered());
    var returnType = String.class;

    LogEvent.log("StockOrderLot", event.stockOrderLotId().toEntityId(), "StockOrder", stockOrderId);

    return kalixClient.put(path, command, returnType);
  }
}
