package io.example.stock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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

    return onOneEventInToManyCommandsOut(event);
  }

  private Effect<String> onOneEventInToManyCommandsOut(StockOrderEntity.GeneratedStockSkuItemIdsEvent event) {
    var results = event.generateStockSkuItems().stream()
        .map(id -> toCommand(id))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  private StockSkuItemEntity.CreateStockSkuItemCommand toCommand(StockOrderEntity.GenerateStockSkuItem id) {
    return new StockSkuItemEntity.CreateStockSkuItemCommand(id.stockSkuItemId(), id.skuId(), id.skuName());
  }

  private CompletionStage<String> callFor(StockSkuItemEntity.CreateStockSkuItemCommand command) {
    var path = "/stock-sku-item/%s/create".formatted(command.stockSkuItemId().toEntityId());
    var returnType = String.class;
    var deferredCall = kalixClient.post(path, command, returnType);

    return deferredCall.execute();
  }

  private CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> "OK");
  }
}
