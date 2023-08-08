package io.example.stock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockOrderEntity.class, ignoreUnknown = true)
public class StockOrderToStockSkuItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderToStockSkuItemAction.class);
  private final ComponentClient componentClient;

  public StockOrderToStockSkuItemAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockOrderEntity.GeneratedStockSkuItemIdsEvent event) {
    log.info("Event: {}", event);

    var results = event.generateStockSkuItems().stream()
        .map(id -> toCommand(id))
        .map(command -> callFor(command, event.stockOrderId()))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  private StockSkuItemEntity.CreateStockSkuItemCommand toCommand(StockOrderEntity.GenerateStockSkuItem id) {
    return new StockSkuItemEntity.CreateStockSkuItemCommand(id.stockSkuItemId(), id.skuId(), id.skuName());
  }

  private CompletionStage<String> callFor(StockSkuItemEntity.CreateStockSkuItemCommand command, String stockOrderId) {
    LogEvent.log("StockOrder", stockOrderId, "StockSkuItem", command.stockSkuItemId().toEntityId(), "");
    return componentClient.forEventSourcedEntity(command.stockSkuItemId().toEntityId())
        .call(StockSkuItemEntity::create)
        .params(command)
        .execute();
  }

  private CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
        .thenApply(__ -> "OK");
  }
}
