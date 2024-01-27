package io.example.shipping;

import java.util.Random;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.stock.StockOrderRedLeafEntity;
import io.example.shipping.OrderItemRedLeafBackOrderedView.OrderItemRedLeafRow;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockOrderRedLeafEntity.class, ignoreUnknown = true)
public class StockOrderRedLeafToOrderItemRedLeafAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderRedLeafToOrderItemRedLeafAction.class);
  private static final Random random = new Random();
  private final ComponentClient componentClient;

  public StockOrderRedLeafToOrderItemRedLeafAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent event) {
    log.info("Event: {}", event);

    return queryView(event);
  }

  public Effect<String> on(StockOrderRedLeafEntity.StockOrderReleasedOrderSkuItemsEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockOrderRedLeaf", event.stockOrderRedLeafId().toEntityId(), "OrderItemRedLeaf", event.orderItemRedLeafId().toEntityId(), "color green");

    return callFor(event);
  }

  Effect<String> queryView(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent event) {
    return effects().asyncReply(
        componentClient.forView()
            .call(OrderItemRedLeafBackOrderedView::getOrderItemRedLeafBackOrdered)
            .params(event.stockOrderRedLeafId().skuId())
            .execute()
            .thenCompose(queryResults -> processQueryResults(event, queryResults)));
  }

  CompletionStage<String> processQueryResults(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent event, OrderItemRedLeafBackOrderedView.OrderItemRedLeafRows queryReply) {
    var count = queryReply.orderItemRedLeafRows().size();
    if (count > 0) {
      var randomIndex = random.nextInt(count);
      var orderItemRedLeafRow = queryReply.orderItemRedLeafRows().get(randomIndex);
      log.info("Found {} order item leaf branches, skuId: {}\n_for stock order: {}\n_attempt join to order item leaf branch: {}",
          count, event.stockOrderRedLeafId().skuId(), event.stockOrderRedLeafId().toEntityId(), orderItemRedLeafRow.orderItemRedLeafId().toEntityId());
      return callFor(event, orderItemRedLeafRow);
    } else {
      log.info("No back ordered order items available, skuId: {}, setting stock order to available: {}", event.stockOrderRedLeafId().skuId(), event.stockOrderRedLeafId().toEntityId());
      return callFor(event);
    }
  }

  CompletionStage<String> callFor(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent event, OrderItemRedLeafRow orderItemRedLeafRow) {
    var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(
        orderItemRedLeafRow.orderItemRedLeafId(), event.stockOrderRedLeafId(), event.stockSkuItemIds());

    return componentClient.forEventSourcedEntity(orderItemRedLeafRow.orderItemRedLeafId().toEntityId())
        .call(OrderItemRedLeafEntity::stockOrderRequestsOrderSkuItems)
        .params(command)
        .execute();
  }

  CompletionStage<String> callFor(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent event) {
    var command = new StockOrderRedLeafEntity.StockOrderSetAvailableToBeConsumedCommand(event.stockOrderRedLeafId());

    return componentClient.forEventSourcedEntity(event.stockOrderRedLeafId().toEntityId())
        .call(StockOrderRedLeafEntity::stockOrderSetAvailableToBeConsumed)
        .params(command)
        .execute();
  }

  Effect<String> callFor(StockOrderRedLeafEntity.StockOrderReleasedOrderSkuItemsEvent event) {
    var command = new OrderItemRedLeafEntity.StockOrderReleaseOrderSkuItemsCommand(event.orderItemRedLeafId(), event.stockOrderRedLeafId());

    return effects().asyncReply(
        componentClient.forEventSourcedEntity(event.orderItemRedLeafId().toEntityId())
            .call(OrderItemRedLeafEntity::stockOrderReleaseOrderSkuItems)
            .params(command)
            .execute());
  }

  Effect<String> callFor(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent event) {
    var orderSkuItemsToStockSkuItems = event.consumed().stockSkuItemsToOrderSkuItems().stream()
        .map(stockSkuItemToOrderSkuItem -> new OrderItemRedLeafEntity.OrderSkuItemToStockSkuItem(
            stockSkuItemToOrderSkuItem.orderSkuItemId(), stockSkuItemToOrderSkuItem.stockSkuItemId()))
        .toList();
    var command = new OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsCommand(event.orderItemRedLeafId(), event.stockOrderRedLeafId(), orderSkuItemsToStockSkuItems);

    return effects().asyncReply(
        componentClient.forEventSourcedEntity(event.orderItemRedLeafId().toEntityId())
            .call(OrderItemRedLeafEntity::orderItemConsumedStockSkuItems)
            .params(command)
            .execute());
  }
}