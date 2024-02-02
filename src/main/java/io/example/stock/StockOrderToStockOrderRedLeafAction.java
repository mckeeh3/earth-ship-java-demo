package io.example.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockOrderEntity.class, ignoreUnknown = true)
public class StockOrderToStockOrderRedLeafAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderToStockOrderRedLeafAction.class);
  private final ComponentClient componentClient;

  public StockOrderToStockOrderRedLeafAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockOrderEntity.GeneratedStockSkuItemIdsEvent event) {
    log.info("Event: {}", event);
    return callFor(event);
  }

  private Effect<String> callFor(StockOrderEntity.GeneratedStockSkuItemIdsEvent event) {
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId(event.stockOrderId(), event.skuId(), event.quantityTotal(), event.quantityTotal());
    var stockSkuItemIds = event.stockSkuItemIds().stream()
        .map(s -> StockOrderRedLeafEntity.StockSkuItemId.of(s.stockOrderId(), s.skuId(), s.uuid()))
        .toList();
    var command = new StockOrderRedLeafEntity.StockOrderCreateCommand(stockOrderRedLeafId, stockSkuItemIds);

    LogEvent.log("StockOrder", event.stockOrderId(), "StockOrderRedLeaf", stockOrderRedLeafId.toEntityId(), "");

    return effects().forward(
        componentClient.forEventSourcedEntity(stockOrderRedLeafId.toEntityId())
            .call(StockOrderRedLeafEntity::stockOrderCreate)
            .params(command));
  }
}