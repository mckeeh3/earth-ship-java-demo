package io.example.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.LogEvent;
import io.example.shipping.OrderSkuItemEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = StockSkuItemEntity.class, ignoreUnknown = true)
public class StockSkuItemToStockOrderLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemToStockOrderLotAction.class);
  private final KalixClient kalixClient;

  public StockSkuItemToStockOrderLotAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockSkuItem", event.stockSkuItemId().toEntityId(), "StockOrderLot", event.stockSkuItemId().stockOrderLotId().levelUp().toEntityId(), "color green");
    return effects().forward(callFor(event.stockSkuItemId().stockOrderLotId(), true));
  }

  public Effect<String> on(StockSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockSkuItem", event.stockSkuItemId().toEntityId(), "StockOrderLot", event.stockSkuItemId().stockOrderLotId().levelUp().toEntityId(), "color green");
    return effects().forward(callFor(event.stockSkuItemId().stockOrderLotId(), true));
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event.stockSkuItemId().stockOrderLotId(), false));
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event.stockSkuItemId().stockOrderLotId(), false));
  }

  private DeferredCall<Any, String> callFor(StockOrderLotId subStockOrderLotId, boolean acceptedOrReleased) {
    var totalOrdered = acceptedOrReleased ? 1 : 0;
    var upperStockOrderLotId = subStockOrderLotId.levelUp();
    var subStockOrderLot = new StockOrderLot(subStockOrderLotId, 1, totalOrdered, List.of());

    var path = "/stock-order-lot/%s/update".formatted(upperStockOrderLotId.toEntityId());
    var command = new StockOrderLotEntity.UpdateSubStockOrderLotCommand(subStockOrderLotId, subStockOrderLot);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }
}
