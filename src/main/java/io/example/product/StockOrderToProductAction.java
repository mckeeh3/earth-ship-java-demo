package io.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.stock.StockOrderEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockOrderEntity.class, ignoreUnknown = true)
public class StockOrderToProductAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderToProductAction.class);
  private final ComponentClient componentClient;

  public StockOrderToProductAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockOrderEntity.CreatedStockOrderEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockOrder", event.stockOrderId(), "Product", event.skuId(), "");

    return callFor(event);
  }

  public Effect<String> on(StockOrderEntity.UpdatedStockOrderEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockOrder", event.stockOrderId(), "Product", event.skuId(), "");

    return callFor(event);
  }

  private Effect<String> callFor(StockOrderEntity.CreatedStockOrderEvent event) {
    var command = new ProductEntity.AddStockOrderCommand(event.stockOrderId(), event.skuId(), event.quantityTotal());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.skuId())
            .call(ProductEntity::addStockOrder)
            .params(command));
  }

  private Effect<String> callFor(StockOrderEntity.UpdatedStockOrderEvent event) {
    var command = new ProductEntity.UpdateStockOrderCommand(event.stockOrderId(), event.skuId(), event.quantityOrdered());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.skuId())
            .call(ProductEntity::updateStockOrder)
            .params(command));
  }
}
