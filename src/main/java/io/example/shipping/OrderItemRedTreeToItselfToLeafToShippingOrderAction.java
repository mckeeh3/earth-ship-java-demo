package io.example.shipping;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = OrderItemRedTreeEntity.class, ignoreUnknown = true)
public class OrderItemRedTreeToItselfToLeafToShippingOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderItemRedTreeToItselfToLeafToShippingOrderAction.class);
  private final ComponentClient componentClient;

  public OrderItemRedTreeToItselfToLeafToShippingOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderItemRedTreeEntity.OrderItemRedTreeCreatedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(OrderItemRedTreeEntity.OrderItemSubBranchUpdatedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  Effect<String> callFor(OrderItemRedTreeEntity.OrderItemRedTreeCreatedEvent event) {
    var resultsTree = event.suBranches().stream()
        .filter(subBranch -> subBranch.quantity() > OrderItemRedTreeEntity.SubBranch.maxLeavesPerBranch)
        .map(subBranch -> toCommandForTree(event, subBranch))
        .map(command -> callForTree(command))
        .toList();

    var resultsLeaf = event.suBranches().stream()
        .filter(subBranch -> subBranch.quantity() <= OrderItemRedTreeEntity.SubBranch.maxLeavesPerBranch)
        .map(subBranch -> toCommandForLeaf(event, subBranch))
        .map(command -> callForLeaf(command))
        .toList();

    var results = Stream.of(resultsTree, resultsLeaf)
        .flatMap(List::stream)
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  CompletionStage<String> callForTree(OrderItemRedTreeEntity.OrderItemCreateCommand command) {
    LogEvent.log("OrderItemRedTree", command.orderItemRedTreeId().orderId(), "ShippingOrder", command.orderItemRedTreeId().toEntityId(), "color yellow");

    return componentClient.forEventSourcedEntity(command.orderItemRedTreeId().toEntityId())
        .call(OrderItemRedTreeEntity::orderItemCreate)
        .params(command)
        .execute();
  }

  CompletionStage<String> callForLeaf(OrderItemRedLeafEntity.OrderItemCreateCommand command) {
    LogEvent.log("OrderItemRedLeaf", command.orderItemRedLeafId().orderId(), "ShippingOrder", command.orderItemRedLeafId().toEntityId(), "color yellow");

    return componentClient.forEventSourcedEntity(command.orderItemRedLeafId().toEntityId())
        .call(OrderItemRedLeafEntity::orderItemCreate)
        .params(command)
        .execute();
  }

  OrderItemRedTreeEntity.OrderItemCreateCommand toCommandForTree(OrderItemRedTreeEntity.OrderItemRedTreeCreatedEvent event, OrderItemRedTreeEntity.SubBranch subBranch) {
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of(event.orderItemRedTreeId().orderId(), event.orderItemRedTreeId().skuId());
    var parentId = event.orderItemRedTreeId();

    return new OrderItemRedTreeEntity.OrderItemCreateCommand(
        orderItemRedTreeId,
        parentId,
        subBranch.quantity());
  }

  OrderItemRedLeafEntity.OrderItemCreateCommand toCommandForLeaf(OrderItemRedTreeEntity.OrderItemRedTreeCreatedEvent event, OrderItemRedTreeEntity.SubBranch subBranch) {
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of(event.orderItemRedTreeId().orderId(), event.orderItemRedTreeId().skuId());

    return new OrderItemRedLeafEntity.OrderItemCreateCommand(
        orderItemRedLeafId,
        event.orderItemRedTreeId(),
        subBranch.quantity());
  }

  CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");
  }

  Effect<String> callFor(OrderItemRedTreeEntity.OrderItemSubBranchUpdatedEvent event) {
    if (event.parentId() == null) {
      return callForShippingOrder(event);
    }
    return callForParentBranch(event);
  }

  private Effect<String> callForShippingOrder(OrderItemRedTreeEntity.OrderItemSubBranchUpdatedEvent event) {
    var orderId = event.orderItemRedTreeId().orderId();
    var skuId = event.orderItemRedTreeId().skuId();
    var readyToShipAt = event.quantityBackOrdered() == 0 && event.quantityReadyToShip() == event.quantity() ? Instant.now() : null;
    var backOrderedAt = event.quantityBackOrdered() > 0 ? Instant.now() : null;
    var command = new ShippingOrderEntity.OrderItemUpdateCommand(orderId, skuId, readyToShipAt, backOrderedAt);

    return effects().forward(
        componentClient.forEventSourcedEntity(event.orderItemRedTreeId().orderId())
            .call(ShippingOrderEntity::orderItemUpdate)
            .params(command));
  }

  private Effect<String> callForParentBranch(OrderItemRedTreeEntity.OrderItemSubBranchUpdatedEvent event) {
    var command = new OrderItemRedTreeEntity.OrderItemSubBranchUpdateCommand(event.orderItemRedTreeId(), event.parentId(),
        event.quantity(), event.quantityReadyToShip(), event.quantityBackOrdered());

    return effects().forward(
        componentClient.forEventSourcedEntity(event.parentId().toEntityId())
            .call(OrderItemRedTreeEntity::orderItemSubBranchUpdate)
            .params(command));
  }
}