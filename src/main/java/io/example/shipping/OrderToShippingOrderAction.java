package io.example.shipping;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.order.OrderEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

//@Subscribe.EventSourcedEntity(value = OrderEntity.class, ignoreUnknown = true)
public class OrderToShippingOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderToShippingOrderAction.class);
  private final ComponentClient componentClient;

  public OrderToShippingOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderEntity.CreatedOrderEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("Order", event.orderId(), "ShippingOrder", event.orderId(), "color yellow");

    return callFor(event);
  }

  private Effect<String> callFor(OrderEntity.CreatedOrderEvent event) {
    return effects().forward(
        componentClient.forEventSourcedEntity(event.orderId())
            .call(ShippingOrderEntity::createOrder)
            .params(toCommand(event)));
  }

  private ShippingOrderEntity.CreateShippingOrderCommand toCommand(OrderEntity.CreatedOrderEvent event) {
    return new ShippingOrderEntity.CreateShippingOrderCommand(event.orderId(), event.customerId(), event.orderedAt(), toOrderItems(event.orderItems()));
  }

  private List<ShippingOrderEntity.OrderItem> toOrderItems(List<OrderEntity.OrderItem> items) {
    return items.stream().map(i -> new ShippingOrderEntity.OrderItem(i.skuId(), i.skuName(), i.quantity(), null, null)).toList();
  }
}
