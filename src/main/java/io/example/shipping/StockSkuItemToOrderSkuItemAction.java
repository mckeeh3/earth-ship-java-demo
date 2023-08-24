package io.example.shipping;

import java.util.Random;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.stock.StockSkuItemEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = StockSkuItemEntity.class, ignoreUnknown = true)
public class StockSkuItemToOrderSkuItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemToOrderSkuItemAction.class);
  private static final Random random = new Random();
  private final ComponentClient componentClient;

  public StockSkuItemToOrderSkuItemAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(StockSkuItemEntity.StockRequestedJoinToOrderEvent event) {
    log.info("Event: {}", event);

    return queryView(event);
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockSkuItem", event.stockSkuItemId().toEntityId(), "OrderSkuItem", event.orderSkuItemId().toEntityId(), "color green");

    return callFor(event);
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(StockSkuItemEntity.StockRequestedJoinToOrderReleasedEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> queryView(StockSkuItemEntity.StockRequestedJoinToOrderEvent event) {
    return effects().asyncReply(
        componentClient.forView()
            .call(OrderSkuItemsBackOrderedView::getOrderSkuItemsBackOrdered)
            .params(event.skuId())
            .execute()
            .thenCompose(queryResults -> processQueryResults(event, queryResults)));
  }

  private CompletionStage<String> processQueryResults(StockSkuItemEntity.StockRequestedJoinToOrderEvent event, OrderSkuItemsBackOrderedView.OrderSkuItemRows queryReply) {
    var count = queryReply.orderSkuItemRows().size();
    if (count > 0) {
      var orderSkuItemRow = queryReply.orderSkuItemRows().get(random.nextInt(count));
      log.info("Found {} back-ordered order sku items, skuId: {}\n_stock sku item: {}\n_attempt to join to stock sku item: {}",
          count, event.skuId(), event.stockSkuItemId().toEntityId(), orderSkuItemRow.orderSkuItemId().toEntityId());
      return callFor(event, orderSkuItemRow);
    } else {
      log.info("No back-ordered order sku items, skuId: {}, stock sku item: {}", event.skuId(), event.stockSkuItemId().toEntityId());
      return callFor(event);
    }
  }

  private CompletionStage<String> callFor(StockSkuItemEntity.StockRequestedJoinToOrderEvent event, OrderSkuItemsBackOrderedView.OrderSkuItemRow orderSkuItemRow) {
    var command = new OrderSkuItemEntity.StockRequestsJoinToOrderCommand(orderSkuItemRow.orderSkuItemId(), event.skuId(), event.stockSkuItemId());
    return componentClient.forEventSourcedEntity(orderSkuItemRow.orderSkuItemId().toEntityId())
        .call(OrderSkuItemEntity::stockRequestsJoinToOrder)
        .params(command)
        .execute();
  }

  private CompletionStage<String> callFor(StockSkuItemEntity.StockRequestedJoinToOrderEvent event) {
    var command = new StockSkuItemEntity.StockSkuItemActivateCommand(event.stockSkuItemId(), event.skuId());
    return componentClient.forEventSourcedEntity(event.stockSkuItemId().toEntityId())
        .call(StockSkuItemEntity::activate)
        .params(command)
        .execute();
  }

  private Effect<String> callFor(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    var command = new OrderSkuItemEntity.OrderRequestsJoinToStockAcceptedCommand(event.orderSkuItemId(), event.skuId(), event.stockSkuItemId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.orderSkuItemId().toEntityId())
            .call(OrderSkuItemEntity::orderRequestedJoinToStockAccepted)
            .params(command));
  }

  private Effect<String> callFor(StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    var command = new OrderSkuItemEntity.OrderRequestsJoinToStockRejectedCommand(event.orderSkuItemId(), event.skuId(), event.stockSkuItemId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.orderSkuItemId().toEntityId())
            .call(OrderSkuItemEntity::orderRequestedJoinToStockRejected)
            .params(command));
  }

  private Effect<String> callFor(StockSkuItemEntity.StockRequestedJoinToOrderReleasedEvent event) {
    var command = new OrderSkuItemEntity.StockRequestsJoinToOrderReleasedCommand(event.orderSkuItemId(), event.skuId(), event.stockSkuItemId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.orderSkuItemId().toEntityId())
            .call(OrderSkuItemEntity::stockRequestsJoinToOrderReleased)
            .params(command));
  }
}
