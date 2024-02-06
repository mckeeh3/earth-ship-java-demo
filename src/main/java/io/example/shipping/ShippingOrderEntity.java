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
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;
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
  public Effect<String> orderCreate(@RequestBody shippingOrderCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-update")
  public Effect<String> orderItemUpdate(@RequestBody OrderItemUpdateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/ready-to-ship-order-item")
  public Effect<String> readyToShipOrderItem(@RequestBody ReadyToShipOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/release-order-item")
  public Effect<String> releaseOrderItem(@RequestBody ReleaseOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/back-order-order-item")
  public Effect<String> backOrderOrderItem(@RequestBody BackOrderOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetShippingOrder", entityId, currentState());
    return Validator
        .isTrue(currentState().isEmpty(), "ShippingOrder is not found")
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @EventHandler
  public State on(ShippingOrderCreatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemReadyToShipEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderReadyToShipEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemUpdatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderUpdatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemBackOrderedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderBackOrderedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
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

    List<Event> eventsFor(shippingOrderCreateCommand command) {
      if (isAlreadyCreated()) {
        return List.of();
      }
      return List.of(new ShippingOrderCreatedEvent(command.orderId(), command.customerId(), command.orderedAt(), toOrderItems(command)));
    }

    List<Event> eventsFor(ReadyToShipOrderItemCommand command) {
      var event = new OrderItemReadyToShipEvent(command.orderId(), command.skuId(), command.readyToShipAt(), List.of());
      var newState = on(event);
      if (newState.readyToShip()) {
        return List.of(event, new OrderReadyToShipEvent(command.orderId(), command.readyToShipAt()));
      }
      return List.of(event);
    }

    private boolean readyToShip() {
      return orderItems.stream().allMatch(i -> i.readyToShipAt != null);
    }

    List<Event> eventsFor(ReleaseOrderItemCommand command) {
      var event = new OrderItemUpdatedEvent(command.orderId(), command.skuId(), null, null, List.of());
      var newState = on(event);
      if (newState.released()) {
        return List.of(event, new OrderUpdatedEvent(command.orderId()));
      }
      return List.of(event);
    }

    private boolean released() {
      return orderItems.stream().allMatch(i -> i.readyToShipAt == null);
    }

    List<Event> eventsFor(BackOrderOrderItemCommand command) {
      var event = new OrderItemBackOrderedEvent(command.orderId(), command.skuId(), command.backOrderedAt(), List.of());
      var newState = on(event);
      if (newState.backOrdered()) {
        return List.of(event, new OrderBackOrderedEvent(command.orderId(), command.backOrderedAt()));
      }
      return List.of(event);
    }

    private boolean backOrdered() {
      return orderItems.stream().anyMatch(i -> i.backOrderedAt != null);
    }

    List<Event> eventsFor(OrderItemUpdateCommand command) {
      var newOrderItems = orderItems.stream()
          .map(i -> i.skuId().equals(command.skuId)
              ? new OrderItem(
                  i.skuId(),
                  i.skuName(),
                  i.quantity(),
                  command.readyToShipAt,
                  command.backOrderedAt)
              : i)
          .toList();
      var newBackOrderedAt = newOrderItems.stream().anyMatch(i -> i.backOrderedAt() != null) ? command.backOrderedAt : null;
      var newReadyToShipAt = newBackOrderedAt == null && newOrderItems.stream().allMatch(i -> i.readyToShipAt() != null) ? command.readyToShipAt : null;

      if (instantsChanged(newReadyToShipAt, readyToShipAt)) {
        var event = new OrderItemReadyToShipEvent(command.orderId(), command.skuId(), newReadyToShipAt, newOrderItems);

        return newReadyToShipAt == null
            ? List.of(event)
            : List.of(event, new OrderReadyToShipEvent(command.orderId(), newReadyToShipAt));
      }

      if (instantsChanged(newBackOrderedAt, readyToShipAt)) {
        var event = new OrderItemBackOrderedEvent(command.orderId(), command.skuId(), newBackOrderedAt, newOrderItems);

        return newBackOrderedAt == null
            ? List.of(event)
            : List.of(event, new OrderBackOrderedEvent(command.orderId(), newBackOrderedAt));
      }

      return List.of(
          new OrderItemUpdatedEvent(command.orderId(), command.skuId(), command.readyToShipAt(), command.backOrderedAt(), List.of()));
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

    State on(OrderItemReadyToShipEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          event.readyToShipAt(),
          null,
          event.orderItems());
    }

    State on(OrderItemBackOrderedEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          null,
          event.backOrderedAt(),
          event.orderItems());
    }

    State on(OrderItemUpdatedEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          event.readyToShipAt,
          event.backOrderedAt,
          event.orderItems);
    }

    State on(OrderReadyToShipEvent event) {
      return this;
    }

    State on(OrderUpdatedEvent event) {
      return this;
    }

    State on(OrderBackOrderedEvent event) {
      return this;
    }

    private List<OrderItem> toOrderItems(shippingOrderCreateCommand command) {
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

  public record shippingOrderCreateCommand(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) {}

  public record ShippingOrderCreatedEvent(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) implements Event {}

  public record ReadyToShipOrderItemCommand(String orderId, String skuId, Instant readyToShipAt) {}

  public record ReleaseOrderItemCommand(String orderId, String skuId) {}

  public record BackOrderOrderItemCommand(String orderId, String skuId, Instant backOrderedAt) {}

  public record OrderItemUpdateCommand(String orderId, String skuId, Instant readyToShipAt, Instant backOrderedAt) {}

  public record OrderItemUpdatedEvent(String orderId, String skuId, Instant readyToShipAt, Instant backOrderedAt, List<OrderItem> orderItems) implements Event {}

  public record OrderUpdatedEvent(String orderId) implements Event {}

  public record OrderItemReadyToShipEvent(String orderId, String skuId, Instant readyToShipAt, List<OrderItem> orderItems) implements Event {}

  public record OrderReadyToShipEvent(String orderId, Instant readyToShipAt) implements Event {}

  public record OrderItemBackOrderedEvent(String orderId, String skuId, Instant backOrderedAt, List<OrderItem> orderItems) implements Event {}

  public record OrderBackOrderedEvent(String orderId, Instant backOrderedAt) implements Event {}
}
