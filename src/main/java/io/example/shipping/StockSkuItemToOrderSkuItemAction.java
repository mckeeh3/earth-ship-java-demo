package io.example.shipping;

import java.util.Random;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.stock.StockSkuItemEntity;
import io.example.stock.StockSkuItemEntity.StockRequestedJoinToOrderEvent;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

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
    return effects().asyncReply(queryView(event));
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(StockSkuItemEntity.StockRequestedJoinToOrderReleasedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private CompletionStage<String> queryView(StockSkuItemEntity.StockRequestedJoinToOrderEvent event) {
    var path = "/order-sku-items-back-ordered/%s".formatted(event.skuId());
    var returnType = OrderSkuItemsBackOrderedView.OrderSkuItemRows.class;
    return kalixClient.get(path, returnType)
        .execute()
        .thenCompose(queryResults -> processQueryResults(event, queryResults));
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
    var path = "/order-sku-item/%s/stock-requests-join-to-order".formatted(orderSkuItemRow.orderSkuItemId().toEntityId());
    var command = toCommand(event, orderSkuItemRow);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private OrderSkuItemEntity.StockRequestsJoinToOrderCommand toCommand(StockSkuItemEntity.StockRequestedJoinToOrderEvent event, OrderSkuItemsBackOrderedView.OrderSkuItemRow orderSkuItemRow) {
    return new OrderSkuItemEntity.StockRequestsJoinToOrderCommand(
        orderSkuItemRow.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
  }

  private CompletionStage<String> callFor(StockSkuItemEntity.StockRequestedJoinToOrderEvent event) {
    var path = "/stock-sku-item/%s/activate".formatted(event.stockSkuItemId().toEntityId());
    var command = toCommand(event);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private StockSkuItemEntity.StockSkuItemActivateCommand toCommand(StockRequestedJoinToOrderEvent event) {
    return new StockSkuItemEntity.StockSkuItemActivateCommand(
        event.stockSkuItemId(),
        event.skuId());
  }

  private DeferredCall<Any, String> callFor(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    var path = "/order-sku-item/%s/order-requests-join-to-stock-accepted".formatted(event.orderSkuItemId().toEntityId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private OrderSkuItemEntity.OrderRequestsJoinToStockAcceptedCommand toCommand(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    return new OrderSkuItemEntity.OrderRequestsJoinToStockAcceptedCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
  }

  private DeferredCall<Any, String> callFor(StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    var path = "/order-sku-item/%s/order-requests-join-to-stock-rejected".formatted(event.orderSkuItemId().toEntityId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private OrderSkuItemEntity.OrderRequestsJoinToStockRejectedCommand toCommand(StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    return new OrderSkuItemEntity.OrderRequestsJoinToStockRejectedCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
  }

  private DeferredCall<Any, String> callFor(StockSkuItemEntity.StockRequestedJoinToOrderReleasedEvent event) {
    var path = "/order-sku-item/%s/stock-requests-join-to-order-released".formatted(event.orderSkuItemId().toEntityId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private OrderSkuItemEntity.StockRequestsJoinToOrderReleasedCommand toCommand(StockSkuItemEntity.StockRequestedJoinToOrderReleasedEvent event) {
    return new OrderSkuItemEntity.StockRequestsJoinToOrderReleasedCommand(
        event.orderSkuItemId(),
        event.skuId(),
        event.stockSkuItemId());
  }
}
