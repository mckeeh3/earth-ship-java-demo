package io.example.order;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.cart.ShoppingCartEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = ShoppingCartEntity.class, ignoreUnknown = true)
public class ShoppingCartToOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShoppingCartToOrderAction.class);
  private final KalixClient kalixClient;

  public ShoppingCartToOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(ShoppingCartEntity.CheckedOutEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private DeferredCall<Any, String> callFor(ShoppingCartEntity.CheckedOutEvent event) {
    var orderId = UUID.randomUUID().toString();
    var path = "/order/%s/create".formatted(orderId);
    var command = new OrderEntity.CreateOrderCommand(orderId, event.customerId(), toOrderItems(event.items()));
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private List<OrderEntity.OrderItem> toOrderItems(List<ShoppingCartEntity.LineItem> items) {
    return items.stream()
        .map(i -> new OrderEntity.OrderItem(i.skuId(), i.skuName(), i.skuDescription(), i.skuPrice(), i.quantity(), null, null))
        .toList();
  }
}
