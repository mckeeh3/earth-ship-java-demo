package io.example.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.product.ProductEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = ProductEntity.class, ignoreUnknown = true)
public class ProductToStockOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ProductToStockOrderAction.class);
  private final KalixClient kalixClient;

  public ProductToStockOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(ProductEntity.CreateStockOrderRequestedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private DeferredCall<Any, String> callFor(ProductEntity.CreateStockOrderRequestedEvent event) {
    var path = "/stock-order/%s/create".formatted(event.stockOrderId());
    var command = new StockOrderEntity.CreateStockOrderCommand(event.stockOrderId(), event.skuId(), event.skuName(), event.quantityTotal());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }
}
