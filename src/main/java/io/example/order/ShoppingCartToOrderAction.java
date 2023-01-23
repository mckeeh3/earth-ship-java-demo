package io.example.order;

import java.util.List;

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

    var path = "/order/%s/create".formatted(event.cartId());
    var command = new OrderEntity.CreateOrderCommand(event.cartId(), event.customerId(), toOrderItems(event.items()));
    var returnType = String.class;
    var deferredCall = kalixClient.post(path, command, returnType);

    return effects().forward(deferredCall);
  }

  private List<OrderEntity.OrderItem> toOrderItems(List<ShoppingCartEntity.LineItem> items) {
    return items.stream().map(item -> new OrderEntity.OrderItem(item.skuId(), item.skuName(), item.quantity(), null)).toList();
  }
}
