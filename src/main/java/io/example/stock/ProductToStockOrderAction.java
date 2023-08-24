package io.example.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.product.ProductEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = ProductEntity.class, ignoreUnknown = true)
public class ProductToStockOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ProductToStockOrderAction.class);
  private final ComponentClient componentClient;

  public ProductToStockOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(ProductEntity.CreateStockOrderRequestedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(ProductEntity.CreateStockOrderRequestedEvent event) {
    var command = new StockOrderEntity.CreateStockOrderCommand(event.stockOrderId(), event.skuId(), event.skuName(), event.quantityTotal());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.stockOrderId())
            .call(StockOrderEntity::create)
            .params(command));
  }
}
