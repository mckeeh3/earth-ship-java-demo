package io.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = BackOrderedRedTreeEntity.class, ignoreUnknown = true)
public class BackOrderedRedTreeToItselfToProductAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(BackOrderedRedTreeToItselfToProductAction.class);
  private final ComponentClient componentClient;

  public BackOrderedRedTreeToItselfToProductAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(BackOrderedRedTreeEntity.UpdatedBranchEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(BackOrderedRedTreeEntity.ReleasedToParentEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(BackOrderedRedTreeEntity.UpdatedBranchEvent event) {
    var parentId = event.backOrderedRedTreeId().levelDown();
    var command = new BackOrderedRedTreeEntity.ReleaseToParentCommand(event.backOrderedRedTreeId(), parentId);
    return effects().forward(
        componentClient.forEventSourcedEntity(event.backOrderedRedTreeId().toEntityId())
            .call(BackOrderedRedTreeEntity::releaseToParent)
            .params(command));
  }

  private Effect<String> callFor(BackOrderedRedTreeEntity.ReleasedToParentEvent event) {
    if (event.parentId().trunkLevel()) {
      return callForProduct(event);
    } else {
      return callForBackOrderedRedTree(event);
    }
  }

  private Effect<String> callForBackOrderedRedTree(BackOrderedRedTreeEntity.ReleasedToParentEvent event) {
    var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(event.subBranchId(), event.parentId(), event.subBranch());

    var message = "color %s".formatted(event.subBranch().quantityBackOrdered() > 0 ? "red" : "green");
    LogEvent.log("BackOrderedRedTree", event.parentId().toEntityId(), "BackOrderedRedTree", event.parentId().toEntityId(), message);

    return effects().forward(
        componentClient.forEventSourcedEntity(event.parentId().toEntityId())
            .call(BackOrderedRedTreeEntity::updateSubBranch)
            .params(command));
  }

  private Effect<String> callForProduct(BackOrderedRedTreeEntity.ReleasedToParentEvent event) {
    var skuId = event.parentId().skuId();
    var command = new ProductEntity.UpdateBackOrderedCommand(skuId, event.subBranch().quantityBackOrdered());

    var message = "color %s".formatted(event.subBranch().quantityBackOrdered() > 0 ? "red" : "green");
    LogEvent.log("BackOrderedRedTree", event.parentId().toEntityId(), "Product", skuId, message);

    return effects().forward(
        componentClient.forEventSourcedEntity(skuId)
            .call(ProductEntity::updateBackOrdered)
            .params(command));
  }
}