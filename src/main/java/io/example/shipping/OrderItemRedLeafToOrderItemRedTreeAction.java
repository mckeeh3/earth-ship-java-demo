package io.example.shipping;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = OrderItemRedLeafEntity.class, ignoreUnknown = true)
public class OrderItemRedLeafToOrderItemRedTreeAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderItemRedLeafToOrderItemRedTreeAction.class);
  private final ComponentClient componentClient;

  public OrderItemRedLeafToOrderItemRedTreeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("OrderItemRedLeaf", event.parentId().orderId(), "OrderItemRedTree", event.orderItemRedLeafId().toEntityId(), "color green");

    return callFor(event);
  }

  public Effect<String> on(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("OrderItemRedLeaf", event.parentId().orderId(), "OrderItemRedTree", event.orderItemRedLeafId().toEntityId(), "color green");

    return callFor(event);
  }

  public Effect<String> on(OrderItemRedLeafEntity.StockOrderReleasedOrderSkuItemsEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("OrderItemRedLeaf", event.parentId().orderId(), "OrderItemRedTree", event.orderItemRedLeafId().toEntityId(), "color yellow");

    return callFor(event);
  }

  public Effect<String> on(OrderItemRedLeafEntity.OrderItemSetBackOrderedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("OrderItemRedLeaf", event.parentId().orderId(), "OrderItemRedTree", event.orderItemRedLeafId().toEntityId(), "color red");

    return callFor(event);
  }

  Effect<String> callFor(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent event) {
    var backOrdered = event.backOrderedAt() != null;
    return callFor(event.orderItemRedLeafId(), event.parentId(), backOrdered, event.orderSkuItemsAvailable(), event.orderSkuItemsConsumed());
  }

  Effect<String> callFor(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent event) {
    var backOrdered = event.backOrderedAt() != null;
    return callFor(event.orderItemRedLeafId(), event.parentId(), backOrdered, event.orderSkuItemsAvailable(), event.orderSkuItemsConsumed());
  }

  Effect<String> callFor(OrderItemRedLeafEntity.StockOrderReleasedOrderSkuItemsEvent event) {
    var backOrdered = false;
    return callFor(event.orderItemRedLeafId(), event.parentId(), backOrdered, event.orderSkuItemsAvailable(), event.orderSkuItemsConsumed());
  }

  Effect<String> callFor(OrderItemRedLeafEntity.OrderItemSetBackOrderedEvent event) {
    var backOrdered = true;
    return callFor(event.orderItemRedLeafId(), event.parentId(), backOrdered, event.orderSkuItemsAvailable(), event.orderSkuItemsConsumed());
  }

  Effect<String> callFor(OrderItemRedLeafEntity.OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeEntity.OrderItemRedTreeId parentId, boolean backOrdered,
      List<OrderItemRedLeafEntity.OrderSkuItemId> orderSkuItemsAvailable, List<OrderItemRedLeafEntity.Consumed> orderSkuItemsConsumed) {
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of(orderItemRedLeafId);
    var quantityAvailable = orderSkuItemsAvailable.size();
    var quantityConsumed = OrderItemRedLeafEntity.Consumed.quantityConsumed(orderSkuItemsConsumed);
    var quantityBackOrdered = backOrdered ? quantityAvailable : 0;
    var quantity = quantityAvailable + quantityConsumed;
    var updateSubBranch = new OrderItemRedTreeEntity.OrderItemSubBranchUpdateCommand(orderItemRedTreeId, parentId, quantity, quantityConsumed, quantityBackOrdered);

    return effects().forward(
        componentClient.forEventSourcedEntity(parentId.toEntityId())
            .call(OrderItemRedTreeEntity::orderItemSubBranchUpdate)
            .params(updateSubBranch));
  }
}