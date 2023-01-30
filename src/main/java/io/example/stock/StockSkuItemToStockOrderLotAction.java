package io.example.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.shipping.OrderSkuItemEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = StockSkuItemEntity.class, ignoreUnknown = true)
public class StockSkuItemToStockOrderLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemToStockOrderLotAction.class);
  private final KalixClient kalixClient;

  public StockSkuItemToStockOrderLotAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("Event: {}", event);
    var deferredCall = acceptedOrRejected(event.stockSkuItemId().stockOrderLotId(), true);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("Event: {}", event);
    var deferredCall = acceptedOrRejected(event.stockSkuItemId().stockOrderLotId(), true);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    log.info("Event: {}", event);
    var deferredCall = acceptedOrRejected(event.stockSkuItemId().stockOrderLotId(), false);

    return effects().forward(deferredCall);
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    log.info("Event: {}", event);
    var deferredCall = acceptedOrRejected(event.stockSkuItemId().stockOrderLotId(), false);

    return effects().forward(deferredCall);
  }

  private DeferredCall<Any, String> acceptedOrRejected(StockOrderLotId subStockOrderLotId, boolean accepted) {
    var totalOrdered = accepted ? 1 : 0;
    var upperStockOrderLotId = subStockOrderLotId.levelUp();
    var subStockOrderLot = new StockOrderLot(subStockOrderLotId, 1, totalOrdered, List.of());
    var path = "/stockOrderLot/%s/updateSubStockOrderLot".formatted(upperStockOrderLotId.toEntityId());
    var command = new StockOrderLotEntity.UpdateSubStockOrderLotCommand(subStockOrderLotId, subStockOrderLot);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall;
  }
}
