package io.example.stock;

import java.util.Random;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.shipping.OrderItemRedLeafEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = OrderItemRedLeafEntity.class, ignoreUnknown = true)
public class OrderItemRedLeafToStockOrderRedLeafAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderItemRedLeafToStockOrderRedLeafAction.class);
  private static final Random random = new Random();
  private final ComponentClient componentClient;

  public OrderItemRedLeafToStockOrderRedLeafAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent event) {
    log.info("Event: {}", event);

    return queryView(event);
  }

  public Effect<String> on(OrderItemRedLeafEntity.OrderItemReleasedStockSkuItemsEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("OrderItemRedLeaf", event.orderItemRedLeafId().toEntityId(), "StockOrderRedLeaf", event.stockOrderRedLeafId().toEntityId(), "color green");

    return callFor(event);
  }

  Effect<String> queryView(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent event) {
    return effects().asyncReply(
        componentClient.forView()
            .call(StockOrderRedLeafAvailableView::getStockOrderRedLeafAvailable)
            .params(event.orderItemRedLeafId().skuId())
            .execute()
            .thenCompose(queryResults -> processQueryResults(event, queryResults)));
  }

  CompletionStage<String> processQueryResults(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent event, StockOrderRedLeafAvailableView.StockOrderRedLeafRows queryReply) {
    var count = queryReply.stockOrderRedLeafRows().size();
    if (count > 0) {
      var randomIndex = random.nextInt(count);
      var stockOrderRedLeafRow = queryReply.stockOrderRedLeafRows().get(randomIndex);
      log.info("Found {} stock order leaf branches, skuId: {}\n_for order item: {}\n_attempt join to stock order leaf branch: {}",
          count, event.orderItemRedLeafId().skuId(), event.orderItemRedLeafId().toEntityId(), stockOrderRedLeafRow.stockOrderRedLeafId().toEntityId());
      return callFor(event, stockOrderRedLeafRow);
    } else {
      log.info("No stock orders available, skuId: {}, back-ordering order item: {}", event.orderItemRedLeafId().skuId(), event.orderItemRedLeafId().toEntityId());
      return callFor(event);
    }
  }

  CompletionStage<String> callFor(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent event, StockOrderRedLeafAvailableView.StockOrderRedLeafRow stockOrderRedLeafRow) {
    var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(
        stockOrderRedLeafRow.stockOrderRedLeafId(), event.orderItemRedLeafId(), event.orderSkuItemIds());

    return componentClient.forEventSourcedEntity(stockOrderRedLeafRow.stockOrderRedLeafId().toEntityId())
        .call(StockOrderRedLeafEntity::orderItemRequestsStockSkuItems)
        .params(command)
        .execute();
  }

  CompletionStage<String> callFor(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent event) {
    var command = new OrderItemRedLeafEntity.OrderItemSetBackOrderedCommand(event.orderItemRedLeafId());

    return componentClient.forEventSourcedEntity(event.orderItemRedLeafId().toEntityId())
        .call(OrderItemRedLeafEntity::orderItemSetBackOrdered)
        .params(command)
        .execute();
  }

  Effect<String> callFor(OrderItemRedLeafEntity.OrderItemReleasedStockSkuItemsEvent event) {
    var command = new StockOrderRedLeafEntity.OrderItemReleaseStockSkuItemsCommand(event.stockOrderRedLeafId(), event.orderItemRedLeafId());

    return effects().forward(
        componentClient.forEventSourcedEntity(event.stockOrderRedLeafId().toEntityId())
            .call(StockOrderRedLeafEntity::orderItemReleaseOrderSkuItems)
            .params(command));
  }

  Effect<String> callFor(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent event) {
    var stockSkuItemToOrderSkuItem = event.consumed().orderSkuItemsToStockSkuItems().stream()
        .map(orderSkuItemToStockSkuItem -> new StockOrderRedLeafEntity.StockSkuItemToOrderSkuItem(
            orderSkuItemToStockSkuItem.stockSkuItemId(), orderSkuItemToStockSkuItem.orderSkuItemId()))
        .toList();
    var command = new StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsCommand(event.stockOrderRedLeafId(), event.orderItemRedLeafId(), stockSkuItemToOrderSkuItem);

    return effects().forward(
        componentClient.forEventSourcedEntity(event.stockOrderRedLeafId().toEntityId())
            .call(StockOrderRedLeafEntity::stockOrderConsumedOrderSkuItems)
            .params(command));
  }
}