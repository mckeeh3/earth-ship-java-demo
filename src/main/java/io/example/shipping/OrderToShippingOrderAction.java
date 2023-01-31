package io.example.shipping;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.order.OrderEntity;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = OrderEntity.class, ignoreUnknown = true)
public class OrderToShippingOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderToShippingOrderAction.class);
  private final KalixClient kalixClient;

  public OrderToShippingOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(OrderEntity.CreatedOrderEvent event) {
    log.info("Event: {}", event);

    var path = "/shipping-order/%s/create".formatted(event.orderId());
    var command = new ShippingOrderEntity.CreateOrderCommand(event.orderId(), event.customerId(), event.orderedAt(), toOrderItems(event.items()));
    var returnType = String.class;
    var deferredCall = kalixClient.post(path, command, returnType);

    return effects().forward(deferredCall);
  }

  private List<ShippingOrderEntity.OrderItem> toOrderItems(List<OrderEntity.OrderItem> items) {
    return items.stream().map(i -> new ShippingOrderEntity.OrderItem(i.skuId(), i.skuName(), i.quantity(), null, null, List.of())).toList();
  }
}
