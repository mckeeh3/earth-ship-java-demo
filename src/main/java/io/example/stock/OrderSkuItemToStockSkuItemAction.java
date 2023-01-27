package io.example.stock;

import java.util.Random;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.shipping.OrderSkuItemEntity;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = OrderSkuItemEntity.class, ignoreUnknown = true)
public class OrderSkuItemToStockSkuItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderSkuItemToStockSkuItemAction.class);
  private static final Random random = new Random();
  private final KalixClient kalixClient;

  public OrderSkuItemToStockSkuItemAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    log.info("Event: {}", event);
    return effects().asyncReply(queryAvailableStockSkuItems(event));
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    log.info("Event: {}", event);
    return orderRequestsJoinToStockRejected(event);
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("Event: {}", event);
    return stockRequestsJoinToOrderAccepted(event);
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderRejectedEvent event) {
    log.info("Event: {}", event);
    return effects().asyncReply(StockRequestedJoinToOrderRejectedEvent(event));
  }

  private CompletionStage<String> queryAvailableStockSkuItems(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    var path = "/stock-sku-items-available/%s".formatted(event.skuId());
    var returnType = StockSkuItemsAvailableView.StockSkuItemRows.class;
    return kalixClient.get(path, returnType)
        .execute()
        .thenCompose(queryReply -> onAvailableStockSkuItems(event, queryReply));
  }

  private CompletionStage<String> onAvailableStockSkuItems(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event, StockSkuItemsAvailableView.StockSkuItemRows queryReply) {
    var count = queryReply.stockSkuItemRows().size();
    if (count > 0) {
      return orderRequestsJoinToStock(event, queryReply.stockSkuItemRows().get(random.nextInt(count)));
    } else {
      log.info("No stock available, skuId: {}, back-ordering order sku item: {}", event.skuId(), event.orderSkuItemId());
      return backOrderOrderSkuItem(event);
    }
  }

  private CompletionStage<String> orderRequestsJoinToStock(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event, StockSkuItemsAvailableView.StockSkuItemRow stockSkuItemRow) {
    var path = "/stock-sku-item/%s/order-requests-join-to-stock".formatted(stockSkuItemRow.stockSkuItemId().toEntityId());
    var command = new StockSkuItemEntity.OrderRequestsJoinToStockCommand(
        stockSkuItemRow.stockSkuItemId(),
        event.skuId(),
        event.orderSkuItemId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private CompletionStage<String> backOrderOrderSkuItem(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    var path = "/order-sku-item/%s/back-order-order-sku-item".formatted(event.orderSkuItemId());
    var command = new OrderSkuItemEntity.BackOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private Effect<String> orderRequestsJoinToStockRejected(OrderSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    var path = "/stock-sku-item/%s/order-requests-join-to-stock-rejected".formatted(event.stockSkuItemId().toEntityId());
    var command = new StockSkuItemEntity.OrderRequestsJoinToStockRejectedCommand(
        event.stockSkuItemId(),
        event.skuId(),
        event.orderSkuItemId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  private Effect<String> stockRequestsJoinToOrderAccepted(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    var path = "/stock-sku-item/%s/stock-requests-join-to-order-accepted".formatted(event.stockSkuItemId().toEntityId());
    var command = new StockSkuItemEntity.StockRequestsJoinToOrderAcceptedCommand(
        event.stockSkuItemId(),
        event.skuId(),
        event.orderSkuItemId(),
        event.readyToShipAt());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return effects().forward(deferredCall);
  }

  private CompletionStage<String> StockRequestedJoinToOrderRejectedEvent(OrderSkuItemEntity.StockRequestedJoinToOrderRejectedEvent event) {
    var path = "/stock-sku-item/%s/stock-requests-join-to-order-rejected".formatted(event.stockSkuItemId().toEntityId());
    var command = new StockSkuItemEntity.StockRequestsJoinToOrderRejectedCommand(
        event.stockSkuItemId(),
        event.skuId(),
        event.orderSkuItemId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }
}
