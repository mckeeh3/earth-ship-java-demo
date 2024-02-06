package io.example.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockOrderRedLeafEntity.class, ignoreUnknown = true)
public class StockOrderRedLeafToStockOrderRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderRedLeafToStockOrderRedTreeAction.class);
  private final ComponentClient componentClient;

  public StockOrderRedLeafToStockOrderRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockOrderRedLeaf", event.stockOrderRedLeafId().toEntityId(), "StockOrderRedTree", event.stockOrderRedLeafId().parentId().toEntityId(), "");

    return callFor(event.stockOrderRedLeafId(), event.stockSkuItemsConsumed());
  }

  public Effect<String> on(StockOrderRedLeafEntity.OrderItemReleasedStockSkuItemsEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockOrderRedLeaf", event.stockOrderRedLeafId().toEntityId(), "StockOrderRedTree", event.stockOrderRedLeafId().parentId().toEntityId(), "");

    return callFor(event.stockOrderRedLeafId(), event.stockSkuItemsConsumed());
  }

  public Effect<String> on(StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockOrderRedLeaf", event.stockOrderRedLeafId().toEntityId(), "StockOrderRedTree", event.stockOrderRedLeafId().parentId().toEntityId(), "");

    return callFor(event.stockOrderRedLeafId(), event.stockSkuItemsConsumed());
  }

  Effect<String> callFor(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent event) {
    return callFor(event.stockOrderRedLeafId(), event.stockSkuItemsConsumed());
  }

  Effect<String> callFor(StockOrderRedLeafEntity.OrderItemReleasedStockSkuItemsEvent event) {
    return callFor(event.stockOrderRedLeafId(), event.stockSkuItemsConsumed());
  }

  Effect<String> callFor(StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId, List<StockOrderRedLeafEntity.Consumed> consumed) {
    var quantityConsumed = StockOrderRedLeafEntity.Consumed.quantityConsumed(consumed);

    log.debug("===== {}, tree quantity consumed {}", stockOrderRedLeafId, quantityConsumed); // TODO: remove after testing

    var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
    var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityConsumed);
    var parentId = subBranchId.levelDown();
    var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);

    return effects().forward(
        componentClient.forEventSourcedEntity(parentId.toEntityId())
            .call(StockOrderRedTreeEntity::updateSubBranch)
            .params(command));
  }
}