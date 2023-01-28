package io.example.shipping;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.shipping.OrderSkuItemEntity.CreateOrderSkuItemCommand;
import io.example.shipping.ShippingOrderEntity.CreatedOrderEvent;
import io.example.shipping.ShippingOrderEntity.OrderItem;
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

    var results = event.orderItems().stream()
        .flatMap(orderItem -> toCreateOrderSkuItemCommands(event, orderItem))
        .map(command -> {
          var path = "/order-sku-item/%s/create".formatted(command.orderSkuItemId().toEntityId());
          var returnType = String.class;
          var deferredCall = kalixClient.post(path, command, returnType);
          return deferredCall.execute();
        })
        .toList();

    var result = CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");

    return effects().asyncReply(result);
  }

  private Stream<CreateOrderSkuItemCommand> toCreateOrderSkuItemCommands(CreatedOrderEvent event, OrderItem orderItem) {
    return orderItem.orderSkuItems().stream()
        .map(orderSkuItem -> new CreateOrderSkuItemCommand(
            orderSkuItem.orderSkuItemId(),
            orderSkuItem.customerId(),
            orderSkuItem.skuId(),
            orderSkuItem.skuName(),
            orderSkuItem.orderedAt()));
  }
}
