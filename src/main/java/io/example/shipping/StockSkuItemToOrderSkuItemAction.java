package io.example.shipping;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.shipping.OrderSkuItemsBackOrderedView.OrderSkuItemRow;
import io.example.stock.StockSkuItemEntity;
import io.example.stock.StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent;
import io.example.stock.StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent;
import io.example.stock.StockSkuItemEntity.StockRequestedJoinToOrderEvent;
import io.example.stock.StockSkuItemEntity.StockRequestedJoinToOrderRejectedEvent;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = StockSkuItemEntity.class, ignoreUnknown = true)
public class StockSkuItemToOrderSkuItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemToOrderSkuItemAction.class);
  private static final Random random = new Random();
  private final KalixClient kalixClient;

  public StockSkuItemToOrderSkuItemAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(StockSkuItemEntity.StockRequestedJoinToOrderEvent event) {
    log.info("Event: {}", event);
    return effects().asyncReply(queryBackOrderedOrderSkuItems(event));
  }

  public Effect<String> on(StockSkuItemEntity.StockRequestedJoinToOrderRejectedEvent event) {
    log.info("Event: {}", event);
    return stockRequestsJoinToOrderRejected(event);
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("Event: {}", event);
    return orderRequestsJoinToStockAccepted(event);
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    log.info("Event: {}", event);
    return orderRequestedJoinToStockRejected(event);
  }

  private CompletionStage<String> queryBackOrderedOrderSkuItems(StockSkuItemEntity.StockRequestedJoinToOrderEvent event) {
    var path = "/order-sku-items-back-ordered/%s".formatted(event.skuId());
    var returnType = OrderSkuItemsBackOrderedView.OrderSkuItemRows.class;
    return kalixClient.get(path, returnType)
        .execute()
        .thenCompose(queryReply -> onBackOrderedOrderSkuItems(event, queryReply));
  }

  private CompletionStage<String> onBackOrderedOrderSkuItems(StockSkuItemEntity.StockRequestedJoinToOrderEvent event, OrderSkuItemsBackOrderedView.OrderSkuItemRows queryReply) {
    var count = queryReply.orderSkuItemRows().size();
    if (count > 0) {
      return stockRequestsJoinToOrder(event, queryReply.orderSkuItemRows().get(random.nextInt(count)));
    } else {
      log.info("No back-ordered order sku items, skuId: {}, stock sku item: {}", event.skuId(), event.stockSkuItemId());
      return CompletableFuture.completedFuture("OK");
    }
  }

  private CompletionStage<String> stockRequestsJoinToOrder(StockRequestedJoinToOrderEvent event, OrderSkuItemRow orderSkuItemRow) {
    var path = "/order-sku-item/%s/stock-requests-join-to-order".formatted(orderSkuItemRow.orderSkuItemId().toEntityId());
    var command = new OrderSkuItemEntity.StockRequestsJoinToOrderCommand(
        orderSkuItemRow.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private Effect<String> stockRequestsJoinToOrderRejected(StockRequestedJoinToOrderRejectedEvent event) {
    var path = "/order-sku-item/%s/stock-requests-join-to-order-rejected".formatted(event.orderSkuItemId().toEntityId());
    var command = new OrderSkuItemEntity.StockRequestsJoinToOrderRejectedCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  private Effect<String> orderRequestsJoinToStockAccepted(OrderRequestedJoinToStockAcceptedEvent event) {
    var path = "/order-sku-item/%s/order-requests-join-to-stock-accepted".formatted(event.orderSkuItemId().toEntityId());
    var command = new OrderSkuItemEntity.OrderRequestsJoinToStockAcceptedCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  private Effect<String> orderRequestedJoinToStockRejected(OrderRequestedJoinToStockRejectedEvent event) {
    var path = "/order-sku-item/%s/order-requests-join-to-stock-rejected".formatted(event.orderSkuItemId().toEntityId());
    var command = new OrderSkuItemEntity.OrderRequestsJoinToStockRejectedCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }
}
