package io.example.order;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.cart.ShoppingCartEntity;
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

    var orderId = UUID.randomUUID().toString();
    var path = "/order/%s/create".formatted(orderId);
    var command = new OrderEntity.CreateOrderCommand(orderId, event.customerId(), toOrderItems(event.items()));
    var returnType = String.class;
    var deferredCall = kalixClient.post(path, command, returnType);

    return effects().forward(deferredCall);
  }

  private List<OrderEntity.OrderItem> toOrderItems(List<ShoppingCartEntity.LineItem> items) {
    return items.stream().map(item -> new OrderEntity.OrderItem(item.skuId(), item.skuName(), item.quantity(), null)).toList();
  }
}
