package io.example.stock;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = StockOrderEntity.class, ignoreUnknown = true)
public class StockOrderToStockSkuItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderToStockSkuItemAction.class);
  private final KalixClient kalixClient;

  public StockOrderToStockSkuItemAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(StockOrderEntity.GeneratedStockSkuItemIdsEvent event) {
    log.info("Event: {}", event);

    var results = event.generateStockSkuItems().stream()
        .map(id -> new StockSkuItemEntity.CreateStockSkuItemCommand(id.stockSkuItemId(), id.skuId(), id.skuName()))
        .map(command -> {
          var path = "/stock-sku-item/%s/create".formatted(command.stockSkuItemId().toEntityId());
          var returnType = String.class;
          var deferredCall = kalixClient.post(path, command, returnType);
          return deferredCall.execute();
        })
        .toList();

    var result = CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> "OK");

    return effects().asyncReply(result);
  }
}
