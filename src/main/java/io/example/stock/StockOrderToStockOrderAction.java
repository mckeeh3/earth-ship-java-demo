package io.example.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = StockOrderEntity.class, ignoreUnknown = true)
public class StockOrderToStockOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderToStockOrderAction.class);
  private final KalixClient kalixClient;

  public StockOrderToStockOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(StockOrderEntity.CreatedStockOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event.stockOrderId()));
  }

  public Effect<String> on(StockOrderEntity.GeneratedStockSkuItemIdsEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event.stockOrderId()));
  }

  private DeferredCall<Any, String> callFor(String stockOrderId) {
    var path = "/stockOrder/%s/generate-stock-sku-item-ids".formatted(stockOrderId);
    var command = new StockOrderEntity.GenerateStockSkuItemIdsCommand(stockOrderId);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }
}
