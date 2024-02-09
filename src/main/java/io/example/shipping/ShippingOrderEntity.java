package io.example.shipping;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("orderId")
@TypeId("shippingOrder")
@RequestMapping("/shipping-order/{orderId}")
public class ShippingOrderEntity extends EventSourcedEntity<ShippingOrderEntity.State, ShippingOrderEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderEntity.class);
  private final String entityId;

  public ShippingOrderEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/create")
  public Effect<String> shippingOrderCreate(@RequestBody ShippingOrderCreateCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-update")
  public Effect<String> orderItemUpdate(@RequestBody OrderItemUpdateCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_Get", entityId, currentState());
    return Validator
        .isTrue(currentState().isEmpty(), "ShippingOrder is not found")
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @EventHandler
  public State on(ShippingOrderCreatedEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemUpdatedEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String orderId,
      String customerId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt,
      List<OrderItem> orderItems) {

    static State emptyState() {
      return new State(null, null, null, null, null, List.of());
    }

    boolean isEmpty() {
      return orderId == null || orderId.isEmpty();
    }

    boolean isAlreadyCreated() {
      return orderId != null && !orderId.isEmpty();
    }

    List<Event> eventsFor(ShippingOrderCreateCommand command) {
      if (isAlreadyCreated()) {
        return List.of();
      }
      return List.of(new ShippingOrderCreatedEvent(command.orderId(), command.customerId(), command.orderedAt(), toOrderItems(command)));
    }

    List<Event> eventsFor(OrderItemUpdateCommand command) {
      var newOrderItems = orderItems.stream()
          .map(i -> i.skuId().equals(command.skuId)
              ? new OrderItem(
                  i.skuId(),
                  i.skuName(),
                  i.quantity(),
                  command.readyToShipAt(),
                  command.backOrderedAt())
              : i)
          .toList();
      var newBackOrderedAt = newOrderItems.stream().anyMatch(i -> i.backOrderedAt() != null) ? Instant.now() : null;
      var newReadyToShipAt = newBackOrderedAt == null && newOrderItems.stream().allMatch(i -> i.readyToShipAt() != null) ? Instant.now() : null;

      return List.of(
          new OrderItemUpdatedEvent(command.orderId(), command.skuId(), newReadyToShipAt, newBackOrderedAt, newOrderItems));
    }

    boolean instantsChanged(Instant a, Instant b) {
      return a == null && b != null || a != null && b == null || a != null && b != null && !a.equals(b);
    }

    State on(ShippingOrderCreatedEvent event) {
      if (isEmpty()) {
        return new State(
            event.orderId(),
            event.customerId(),
            event.orderedAt(),
            null,
            null,
            event.orderItems());
      } else {
        return this;
      }
    }

    State on(OrderItemUpdatedEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          event.orderReadyToShipAt(),
          event.orderBackOrderedAt(),
          event.orderItems());
    }

    private List<OrderItem> toOrderItems(ShippingOrderCreateCommand command) {
      return command.orderItems().stream()
          .map(i -> new OrderItem(
              i.skuId(),
              i.skuName(),
              i.quantity(),
              null,
              null))
          .toList();
    }
  }

  public record OrderItem(
      String skuId,
      String skuName,
      int quantity,
      Instant readyToShipAt,
      Instant backOrderedAt) {}

  public interface Event {}

  public record ShippingOrderCreateCommand(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) {}

  public record ShippingOrderCreatedEvent(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) implements Event {}

  public record OrderItemUpdateCommand(String orderId, String skuId, Instant readyToShipAt, Instant backOrderedAt) {}

  public record OrderItemUpdatedEvent(String orderId, String skuId, Instant orderReadyToShipAt, Instant orderBackOrderedAt, List<OrderItem> orderItems) implements Event {}
}
