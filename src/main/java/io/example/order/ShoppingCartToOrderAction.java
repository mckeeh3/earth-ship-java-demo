package io.example.order;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.cart.ShoppingCartEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = ShoppingCartEntity.class, ignoreUnknown = true)
public class ShoppingCartToOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShoppingCartToOrderAction.class);
  private final ComponentClient componentClient;

  public ShoppingCartToOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(ShoppingCartEntity.CheckedOutEvent event) {
    log.info("Event: {}", event);

    var orderId = UUID.randomUUID().toString();
    LogEvent.log("ShoppingCart", event.customerId(), "Order", orderId, "");

    var command = new OrderEntity.CreateOrderCommand(orderId, event.customerId(), toOrderItems(event.items()));
    return effects().forward(
        componentClient.forEventSourcedEntity(orderId)
            .call(OrderEntity::createOrder)
            .params(command));
  }

  private List<OrderEntity.OrderItem> toOrderItems(List<ShoppingCartEntity.LineItem> items) {
    return items.stream()
        .map(i -> new OrderEntity.OrderItem(i.skuId(), i.skuName(), i.skuDescription(), i.skuPrice(), i.quantity(), null, null))
        .toList();
  }
}
