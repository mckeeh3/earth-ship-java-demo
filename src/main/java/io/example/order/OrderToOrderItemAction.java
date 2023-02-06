package io.example.order;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = OrderEntity.class, ignoreUnknown = true)
public class OrderToOrderItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderToOrderItemAction.class);
  private final KalixClient kalixClient;

  public OrderToOrderItemAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(OrderEntity.CreatedOrderEvent event) {
    log.info("Event: {}", event);

    return onOneEventInToManyCommandsOut(event);
  }

  public Effect<String> on(OrderEntity.ReadyToShipOrderItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(OrderEntity.BackOrderedOrderItemEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private Effect<String> onOneEventInToManyCommandsOut(OrderEntity.CreatedOrderEvent event) {
    var results = event.orderItems().stream()
        .map(orderItem -> toCommand(event, orderItem))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  private OrderItemEntity.CreateOrderItemCommand toCommand(OrderEntity.CreatedOrderEvent event, OrderEntity.OrderItem orderItem) {
    return new OrderItemEntity.CreateOrderItemCommand(
        OrderItemId.of(event.orderId(), orderItem.skuId()),
        event.customerId(),
        orderItem.skuId(),
        orderItem.skuName(),
        orderItem.quantity(),
        event.orderedAt());
  }

  private CompletionStage<String> callFor(OrderItemEntity.CreateOrderItemCommand command) {
    var path = "/order-item/%s/create".formatted(command.orderItemId().toEntityId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");
  }

  private DeferredCall<Any, String> callFor(OrderEntity.ReadyToShipOrderItemEvent event) {
    var path = "/order-item/%s/update".formatted(OrderItemId.of(event.orderId(), event.skuId()).toEntityId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private OrderItemEntity.UpdateOrderItemCommand toCommand(OrderEntity.ReadyToShipOrderItemEvent event) {
    return new OrderItemEntity.UpdateOrderItemCommand(
        OrderItemId.of(event.orderId(), event.skuId()), null, null);
  }

  private DeferredCall<Any, String> callFor(OrderEntity.BackOrderedOrderItemEvent event) {
    var path = "/order-item/%s/update".formatted(OrderItemId.of(event.orderId(), event.skuId()).toEntityId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private OrderItemEntity.UpdateOrderItemCommand toCommand(OrderEntity.BackOrderedOrderItemEvent event) {
    return new OrderItemEntity.UpdateOrderItemCommand(
        OrderItemId.of(event.orderId(), event.skuId()), null, event.backOrderedAt());
  }
}
