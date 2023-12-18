package io.example.shipping;

import java.util.List;
import java.util.Optional;

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
        componentClient.forWorkflow(event.orderId())
            .call(ShippingWorkflow::initiateWorkflow)
            .params(toCommand(event)));
  }

  private ShippingWorkflow.CreateWorkflow toCommand(OrderEntity.CreatedOrderEvent event) {
    return new ShippingWorkflow.CreateWorkflow(event.orderId(), toOrderItems(event.orderItems()));
  }

  private List<ShippingWorkflow.OrderItem> toOrderItems(List<OrderEntity.OrderItem> items) {
    return items.stream()
      .map(i -> new ShippingWorkflow.OrderItem(i.skuId(), i.skuName(), i.quantity())).toList();
  }
}
