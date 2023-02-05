package io.example.product;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.shipping.OrderSkuItemEntity;
import io.example.shipping.OrderSkuItemId;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = OrderSkuItemEntity.class, ignoreUnknown = true)
public class OrderSkuItemToBackOrderedLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderSkuItemToBackOrderedLotAction.class);
  private final KalixClient kalixClient;

  public OrderSkuItemToBackOrderedLotAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(OrderSkuItemEntity.BackOrderedOrderSkuItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event.skuId(), event.orderSkuItemId(), event.orderedAt(), true));
  }

  public Effect<String> on(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event.skuId(), event.orderSkuItemId(), event.orderedAt(), false));
  }

  public Effect<String> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event.skuId(), event.orderSkuItemId(), event.orderedAt(), false));
  }

  private DeferredCall<Any, String> callFor(String skuId, OrderSkuItemId orderSkuItemId, Instant orderedAt, boolean backOrdered) {
    var backOrderedLotId = BackOrderedLotId.of(skuId, orderSkuItemId.toEntityId(), orderedAt);
    var upperBackOrderedLotId = backOrderedLotId.levelUp();
    var path = "/back-ordered-lot/%s/update".formatted(upperBackOrderedLotId.toEntityId());
    var command = toCommand(backOrderedLotId, backOrdered);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private BackOrderedLotEntity.UpdateSubBackOrderedLotCommand toCommand(BackOrderedLotId backOrderedLotId, boolean backOrdered) {
    var backOrderedLot = new BackOrderedLot(backOrderedLotId, backOrdered ? 1 : 0, List.of());
    return new BackOrderedLotEntity.UpdateSubBackOrderedLotCommand(backOrderedLotId, backOrderedLot);
  }
}
