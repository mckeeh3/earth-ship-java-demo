package io.example.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.order.OrderEntity;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = OrderEntity.class, ignoreUnknown = true)
public class OrderToGeoOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderToGeoOrderAction.class);
  private final ComponentClient componentClient;

  public OrderToGeoOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(OrderEntity.ReadyToShipOrderEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("Order", event.orderId(), "GeoOrder", event.orderId(), "color green");

    return callFor(event);
  }

  public Effect<String> on(OrderEntity.BackOrderedOrderEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("Order", event.orderId(), "GeoOrder", event.orderId(), "color red");

    return callFor(event);
  }

  private Effect<String> callFor(OrderEntity.ReadyToShipOrderEvent event) {
    var command = new GeoOrderEntity.GeoOrderReadyToShipCommand(event.orderId(), event.readyToShipAt());
    return effects().forward(componentClient.forEventSourcedEntity(event.orderId())
        .call(GeoOrderEntity::readyToShip)
        .params(command));
  }

  private Effect<String> callFor(OrderEntity.BackOrderedOrderEvent event) {
    var command = new GeoOrderEntity.GeoOrderBackOrderedCommand(event.orderId(), event.backOrderedAt());
    return effects().forward(componentClient.forEventSourcedEntity(event.orderId())
        .call(GeoOrderEntity::alarm)
        .params(command));
  }
}
