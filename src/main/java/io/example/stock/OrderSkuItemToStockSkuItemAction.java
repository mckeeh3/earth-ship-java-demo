package io.example.stock;

import java.util.Random;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.shipping.OrderItemRedLeafEntity;
import io.example.shipping.OrderSkuItemEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = OrderItemRedLeafEntity.class, ignoreUnknown = true)
public class OrderSkuItemToStockSkuItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderSkuItemToStockSkuItemAction.class);
  private static final Random random = new Random();
  private final ComponentClient componentClient;

  public OrderSkuItemToStockSkuItemAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    log.info("Event: {}", event);

    return queryView(event);
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("OrderSkuItem", event.orderSkuItemId().toEntityId(), "StockSkuItem", event.stockSkuItemId().toEntityId(), "color green");

    return callFor(event);
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderRejectedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> queryView(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    return effects().asyncReply(
        componentClient.forView()
            .call(StockSkuItemsAvailableView::getStockSkuItemsAvailable)
            .params(event.skuId())
            .execute()
            .thenCompose(queryResults -> processQueryResults(event, queryResults)));
  }

  private CompletionStage<String> processQueryResults(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event, StockSkuItemsAvailableView.StockSkuItemRows queryReply) {
    var count = queryReply.stockSkuItemRows().size();
    if (count > 0) {
      var stockSkuItemRow = queryReply.stockSkuItemRows().get(random.nextInt(count));
      log.info("Found {} stock sku items, skuId: {}\n_for order sku item: {}\n_attempt join to stock order item: {}",
          count, event.skuId(), event.orderSkuItemId().toEntityId(), stockSkuItemRow.stockSkuItemId().toEntityId());
      return callFor(event, stockSkuItemRow);
    } else {
      log.info("No stock available, skuId: {}, back-ordering order sku item: {}", event.skuId(), event.orderSkuItemId().toEntityId());
      return callFor(event);
    }
  }

  private CompletionStage<String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event, StockSkuItemsAvailableView.StockSkuItemRow stockSkuItemRow) {
    var command = new StockSkuItemEntity.OrderRequestsJoinToStockCommand(stockSkuItemRow.stockSkuItemId(), event.skuId(), event.orderSkuItemId());
    return componentClient.forEventSourcedEntity(stockSkuItemRow.stockSkuItemId().toEntityId())
        .call(StockSkuItemEntity::orderRequestsJoinToStock)
        .params(command)
        .execute();
  }

  private CompletionStage<String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    var command = new OrderSkuItemEntity.BackOrderOrderSkuItemCommand(event.orderSkuItemId(), event.skuId());
    return componentClient.forEventSourcedEntity(event.orderSkuItemId().toEntityId())
        .call(OrderSkuItemEntity::backOrderRequested)
        .params(command)
        .execute();
  }

  private Effect<String> callFor(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    var command = new StockSkuItemEntity.StockRequestsJoinToOrderAcceptedCommand(event.stockSkuItemId(), event.skuId(), event.orderSkuItemId(), event.readyToShipAt());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.stockSkuItemId().toEntityId())
            .call(StockSkuItemEntity::stockRequestsJoinToOrderAccepted)
            .params(command));
  }

  private Effect<String> callFor(OrderSkuItemEntity.StockRequestedJoinToOrderRejectedEvent event) {
    var command = new StockSkuItemEntity.StockRequestsJoinToOrderRejectedCommand(event.stockSkuItemId(), event.skuId(), event.orderSkuItemId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.stockSkuItemId().toEntityId())
            .call(StockSkuItemEntity::stockRequestsJoinToOrderRejected)
            .params(command));
  }

  private Effect<String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    var command = new StockSkuItemEntity.OrderRequestsJoinToStockReleasedCommand(event.stockSkuItemId(), event.skuId(), event.orderSkuItemId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.stockSkuItemId().toEntityId())
            .call(StockSkuItemEntity::orderRequestsJoinToStockReleased)
            .params(command));
  }
}
