package io.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.shipping.OrderItemRedLeafEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = OrderItemRedLeafEntity.class, ignoreUnknown = true)
public class OrderItemRedLeafToBackOrderedRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderItemRedLeafToBackOrderedRedTreeAction.class);
  private final ComponentClient componentClient;

  public OrderItemRedLeafToBackOrderedRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderItemRedLeafEntity.OrderItemSetBackOrderedOnEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("OrderItemRedLeaf", event.parentId().toEntityId(), "BackOrderedRedTree", parentIdOf(event.orderItemRedLeafId()).toEntityId(), "color red");

    return callFor(event);
  }

  public Effect<String> on(OrderItemRedLeafEntity.OrderItemSetBackOrderedOffEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("OrderItemRedLeaf", event.parentId().toEntityId(), "BackOrderedRedTree", parentIdOf(event.orderItemRedLeafId()).toEntityId(), "color yellow");

    return callFor(event);
  }

  Effect<String> callFor(OrderItemRedLeafEntity.OrderItemSetBackOrderedOnEvent event) {
    var quantityBackOrdered = event.orderSkuItemsAvailable().size();
    return callFor(event.orderItemRedLeafId(), quantityBackOrdered);
  }

  Effect<String> callFor(OrderItemRedLeafEntity.OrderItemSetBackOrderedOffEvent event) {
    return callFor(event.orderItemRedLeafId(), 0);
  }

  Effect<String> callFor(OrderItemRedLeafEntity.OrderItemRedLeafId orderItemRedLeafId, int quantityBackOrdered) {
    log.info("===== {}, quantity back ordered {}", orderItemRedLeafId, quantityBackOrdered); // TODO: remove after testing

    var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
    var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered);
    var parentId = subBranchId.levelDown();
    var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);

    return effects().forward(
        componentClient.forEventSourcedEntity(parentId.toEntityId())
            .call(BackOrderedRedTreeEntity::updateSubBranch)
            .params(command));
  }

  static BackOrderedRedTreeEntity.BackOrderedRedTreeId parentIdOf(OrderItemRedLeafEntity.OrderItemRedLeafId orderItemRedLeafId) {
    return BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId).levelDown();
  }
}