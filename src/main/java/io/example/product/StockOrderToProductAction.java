package io.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.LogEvent;
import io.example.stock.StockOrderEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = StockOrderEntity.class, ignoreUnknown = true)
public class StockOrderToProductAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderToProductAction.class);
  private final KalixClient kalixClient;

  public StockOrderToProductAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(StockOrderEntity.CreatedStockOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(StockOrderEntity.UpdatedStockOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private DeferredCall<Any, String> callFor(StockOrderEntity.CreatedStockOrderEvent event) {
    var path = "/product/%s/add-stock-order".formatted(event.skuId());
    var command = new ProductEntity.AddStockOrderCommand(event.stockOrderId(), event.skuId(), event.quantityTotal());
    var returnType = String.class;

    LogEvent.log("StockOrder", event.stockOrderId(), "Product", event.skuId(), "");

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(StockOrderEntity.UpdatedStockOrderEvent event) {
    var path = "/product/%s/update-stock-order".formatted(event.skuId());
    var command = new ProductEntity.UpdateStockOrderCommand(event.stockOrderId(), event.skuId(), event.quantityOrdered());
    var returnType = String.class;

    LogEvent.log("StockOrder", event.stockOrderId(), "Product", event.skuId(), "");

    return kalixClient.put(path, command, returnType);
  }
}
