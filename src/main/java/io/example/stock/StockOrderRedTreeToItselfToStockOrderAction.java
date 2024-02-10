package io.example.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockOrderRedTreeEntity.class, ignoreUnknown = true)
public class StockOrderRedTreeToItselfToStockOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderRedTreeToItselfToStockOrderAction.class);
  private final ComponentClient componentClient;

  public StockOrderRedTreeToItselfToStockOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockOrderRedTreeEntity.UpdatedBranchEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(StockOrderRedTreeEntity.ReleasedToParentEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(StockOrderRedTreeEntity.UpdatedBranchEvent event) {
    var parentId = event.stockOrderRedTreeId().levelDown();
    var command = new StockOrderRedTreeEntity.ReleaseToParentCommand(event.stockOrderRedTreeId(), parentId);

    return effects().forward(
        componentClient.forEventSourcedEntity(event.stockOrderRedTreeId().toEntityId())
            .call(StockOrderRedTreeEntity::releaseToParent)
            .params(command));
  }

  private Effect<String> callFor(StockOrderRedTreeEntity.ReleasedToParentEvent event) {
    if (event.subBranchId().trunkLevel()) {
      return callForStockOrder(event);
    } else {
      return callForStockOrderRedTree(event);
    }
  }

  private Effect<String> callForStockOrderRedTree(StockOrderRedTreeEntity.ReleasedToParentEvent event) {
    var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(event.subBranchId(), event.parentId(), event.subBranch());

    LogEvent.log("StockOrderRedTree", event.parentId().toEntityId(), "StockOrderRedTree", event.parentId().toEntityId(), "");

    return effects().forward(
        componentClient.forEventSourcedEntity(event.parentId().toEntityId())
            .call(StockOrderRedTreeEntity::updateSubBranch)
            .params(command));
  }

  private Effect<String> callForStockOrder(StockOrderRedTreeEntity.ReleasedToParentEvent event) {
    var stockOrderId = event.parentId().stockOrderId();
    var command = new StockOrderEntity.UpdateStockOrderCommand(stockOrderId, event.subBranch().quantityConsumed());

    LogEvent.log("StockOrderRedTree", event.parentId().toEntityId(), "StockOrder", stockOrderId, "");

    return effects().forward(
        componentClient.forEventSourcedEntity(stockOrderId)
            .call(StockOrderEntity::update)
            .params(command));
  }
}