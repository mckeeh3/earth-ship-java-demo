package io.example.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.order.OrderEntity;
import io.example.order.OrderEntity.BackOrderedOrderEvent;
import io.example.order.OrderEntity.ReadyToShipOrderEvent;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;
import kalix.springsdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = OrderEntity.class, ignoreUnknown = true)
public class OrderToGeoOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(OrderToGeoOrderAction.class);
  private final KalixClient kalixClient;

  public OrderToGeoOrderAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(OrderEntity.ReadyToShipOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(OrderEntity.BackOrderedOrderEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  private DeferredCall<Any, String> callFor(ReadyToShipOrderEvent event) {
    var path = "/geo-order/%s/ready-to-ship".formatted(event.orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callFor(OrderEntity.BackOrderedOrderEvent event) {
    var path = "/geo-order/%s/back-ordered".formatted(event.orderId());
    var command = toCommand(event);
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private GeoOrderEntity.GeoOrderReadyToShipCommand toCommand(ReadyToShipOrderEvent event) {
    return new GeoOrderEntity.GeoOrderReadyToShipCommand(event.orderId(), event.readyToShipAt());
  }

  private GeoOrderEntity.GeoOrderBackOrderedCommand toCommand(BackOrderedOrderEvent event) {
    return new GeoOrderEntity.GeoOrderBackOrderedCommand(event.orderId(), event.backOrderedAt());
  }
}
