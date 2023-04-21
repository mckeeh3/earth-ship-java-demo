package io.example.shipping;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.order.OrderEntity;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = OrderEntity.class, ignoreUnknown = true)
public class OrderToShippingOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderToShippingOrderAction.class);
  private final KalixClient kalixClient;

  public OrderToShippingOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(OrderEntity.CreatedOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private DeferredCall<Any, String> callFor(OrderEntity.CreatedOrderEvent event) {
    var path = "/shipping-order/%s/create".formatted(event.orderId());
    var command = toCommand(event);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, command, returnType);
    return deferredCall;
  }

  private ShippingOrderEntity.CreateShippingOrderCommand toCommand(OrderEntity.CreatedOrderEvent event) {
    return new ShippingOrderEntity.CreateShippingOrderCommand(event.orderId(), event.customerId(), event.orderedAt(), toOrderItems(event.orderItems()));
  }

  private List<ShippingOrderEntity.OrderItem> toOrderItems(List<OrderEntity.OrderItem> items) {
    return items.stream().map(i -> new ShippingOrderEntity.OrderItem(i.skuId(), i.skuName(), i.quantity(), null, null)).toList();
  }
}
