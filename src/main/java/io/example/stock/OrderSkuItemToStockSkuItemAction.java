package io.example.stock;

import java.util.Random;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.LogEvent;
import io.example.shipping.OrderSkuItemEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

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
    return effects().asyncReply(queryView(event));
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("StockSkuItem", event.stockSkuItemId().toEntityId(), "OrderSkuItem", event.orderSkuItemId().toEntityId());
    return effects().forward(callFor(event));
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderRejectedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private CompletionStage<String> queryView(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    var path = "/stock-sku-items-available/%s".formatted(event.skuId());
    var returnType = StockSkuItemsAvailableView.StockSkuItemRows.class;
    return kalixClient.get(path, returnType)
        .execute()
        .thenCompose(queryResults -> processQueryResults(event, queryResults));
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
    var path = "/stock-sku-item/%s/order-requests-join-to-stock".formatted(stockSkuItemRow.stockSkuItemId().toEntityId());
    var command = toCommand(event, stockSkuItemRow);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private StockSkuItemEntity.OrderRequestsJoinToStockCommand toCommand(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event, StockSkuItemsAvailableView.StockSkuItemRow stockSkuItemRow) {
    return new StockSkuItemEntity.OrderRequestsJoinToStockCommand(
        stockSkuItemRow.stockSkuItemId(),
        event.skuId(),
        event.orderSkuItemId());
  }

  private CompletionStage<String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    var path = "/order-sku-item/%s/back-order-order-sku-item".formatted(event.orderSkuItemId().toEntityId());
    var command = toCommand(event);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private OrderSkuItemEntity.BackOrderOrderSkuItemCommand toCommand(OrderSkuItemEntity.OrderRequestedJoinToStockEvent event) {
    return new OrderSkuItemEntity.BackOrderOrderSkuItemCommand(
        event.orderSkuItemId(),
        event.skuId());
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    var path = "/stock-sku-item/%s/stock-requests-join-to-order-accepted".formatted(event.stockSkuItemId().toEntityId());
    var command = new StockSkuItemEntity.StockRequestsJoinToOrderAcceptedCommand(
        event.stockSkuItemId(),
        event.skuId(),
        event.orderSkuItemId(),
        event.readyToShipAt());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.StockRequestedJoinToOrderRejectedEvent event) {
    var path = "/stock-sku-item/%s/stock-requests-join-to-order-rejected".formatted(event.stockSkuItemId().toEntityId());
    var command = new StockSkuItemEntity.StockRequestsJoinToOrderRejectedCommand(
        event.stockSkuItemId(),
        event.skuId(),
        event.orderSkuItemId());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);

    // return effects().forward(deferredCall);
  }

  private DeferredCall<Any, String> callFor(OrderSkuItemEntity.OrderRequestedJoinToStockReleasedEvent event) {
    var path = "/stock-sku-item/%s/order-requests-join-to-stock-released".formatted(event.stockSkuItemId().toEntityId());
    var command = new StockSkuItemEntity.OrderRequestsJoinToStockReleasedCommand(
        event.stockSkuItemId(),
        event.skuId(),
        event.orderSkuItemId());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);

    // return effects().forward(deferredCall);
  }
}
