package io.example.shipping;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = ShippingOrderEntity.class, ignoreUnknown = true)
public class ShippingOrderToOrderSkuItemAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderToOrderSkuItemAction.class);
  private final KalixClient kalixClient;

  public ShippingOrderToOrderSkuItemAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(ShippingOrderEntity.CreatedOrderEvent event) {
    log.info("Event: {}", event);

    return onOneEventInToManyCommandsOut(event);
  }

  private Effect<String> onOneEventInToManyCommandsOut(ShippingOrderEntity.CreatedOrderEvent event) {
    var results = event.orderItems().stream()
        .flatMap(orderItem -> toCommands(event, orderItem))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  private Stream<OrderSkuItemEntity.CreateOrderSkuItemCommand> toCommands(ShippingOrderEntity.CreatedOrderEvent event, ShippingOrderEntity.OrderItem orderItem) {
    return orderItem.orderSkuItems().stream()
        .map(orderSkuItem -> new OrderSkuItemEntity.CreateOrderSkuItemCommand(
            orderSkuItem.orderSkuItemId(),
            orderSkuItem.customerId(),
            orderSkuItem.skuId(),
            orderSkuItem.skuName(),
            orderSkuItem.orderedAt()));
  }

  private CompletionStage<String> callFor(OrderSkuItemEntity.CreateOrderSkuItemCommand command) {
    var path = "/order-sku-item/%s/create".formatted(command.orderSkuItemId().toEntityId());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);

    return deferredCall.execute();
  }

  private CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");
  }
}
