package io.example.shipping;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.grpc.Status;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@EntityKey("orderId")
@EntityType("shippingOrder")
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
  public Effect<String> createOrder(@RequestBody CreateShippingOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
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
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "ShippingOrder is not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedShippingOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderEvent event) {
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

    CreatedShippingOrderEvent eventFor(CreateShippingOrderCommand command) {
      return new CreatedShippingOrderEvent(command.orderId(), command.customerId(), command.orderedAt(), toOrderItems(command));
    }

    List<? extends Event> eventsFor(ReadyToShipOrderItemCommand command) {
      var event = new ReadyToShipOrderItemEvent(command.orderId(), command.skuId(), command.readyToShipAt());
      var newState = on(event);
      if (newState.readyToShip()) {
        return List.of(event, new ReadyToShipOrderEvent(command.orderId(), command.readyToShipAt()));
      }
      return List.of(event);
    }

    private boolean readyToShip() {
      return orderItems.stream().allMatch(i -> i.readyToShipAt != null);
    }

    List<? extends Event> eventsFor(ReleaseOrderItemCommand command) {
      var event = new ReleasedOrderItemEvent(command.orderId(), command.skuId());
      var newState = on(event);
      if (newState.released()) {
        return List.of(event, new ReleasedOrderEvent(command.orderId()));
      }
      return List.of(event);
    }

    private boolean released() {
      return orderItems.stream().allMatch(i -> i.readyToShipAt == null);
    }

    List<? extends Event> eventsFor(BackOrderOrderItemCommand command) {
      var event = new BackOrderedOrderItemEvent(command.orderId(), command.skuId(), command.backOrderedAt());
      var newState = on(event);
      if (newState.backOrdered()) {
        return List.of(event, new BackOrderedOrderEvent(command.orderId(), command.backOrderedAt()));
      }
      return List.of(event);
    }

    private boolean backOrdered() {
      return orderItems.stream().anyMatch(i -> i.backOrderedAt != null);
    }

    State on(CreatedShippingOrderEvent event) {
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

    State on(ReadyToShipOrderItemEvent event) {
      var newOrderItems = readyToShip(event);
      var newReadyToShipAt = newOrderItems.stream().allMatch(i -> i.readyToShipAt() != null) ? event.readyToShipAt() : null;
      var newBackOrderAt = newOrderItems.stream().anyMatch(i -> i.backOrderedAt() != null) ? backOrderedAt : null;
      return new State(
          orderId,
          customerId,
          orderedAt,
          newBackOrderAt == null ? newReadyToShipAt : null,
          newBackOrderAt,
          newOrderItems);
    }

    State on(ReleasedOrderItemEvent event) {
      var newOrderItems = release(event);
      return new State(
          orderId,
          customerId,
          orderedAt,
          newOrderItems.stream().anyMatch(i -> i.readyToShipAt() == null) ? null : readyToShipAt,
          newOrderItems.stream().allMatch(i -> i.backOrderedAt() != null) ? backOrderedAt : null,
          newOrderItems);
    }

    State on(BackOrderedOrderItemEvent event) {
      var newOrderItems = backOrder(event);
      return new State(
          orderId,
          customerId,
          orderedAt,
          newOrderItems.stream().anyMatch(i -> i.readyToShipAt() == null) ? null : readyToShipAt,
          newOrderItems.stream().allMatch(i -> i.backOrderedAt() != null) ? event.backOrderedAt() : null,
          newOrderItems);
    }

    State on(ReadyToShipOrderEvent event) {
      return this;
    }

    State on(ReleasedOrderEvent event) {
      return this;
    }

    State on(BackOrderedOrderEvent event) {
      return this;
    }

    private List<OrderItem> toOrderItems(CreateShippingOrderCommand command) {
      return command.orderItems().stream()
          .map(i -> new OrderItem(
              i.skuId(),
              i.skuName(),
              i.quantity(),
              null,
              null))
          .toList();
    }

    private List<OrderItem> readyToShip(ReadyToShipOrderItemEvent event) {
      return orderItems.stream()
          .map(i -> i.skuId().equals(event.skuId)
              ? readyToShip(event, i)
              : i)
          .toList();
    }

    private OrderItem readyToShip(ReadyToShipOrderItemEvent event, OrderItem orderItem) {
      return new OrderItem(
          orderItem.skuId(),
          orderItem.skuName(),
          orderItem.quantity(),
          event.readyToShipAt(),
          null);
    }

    private List<OrderItem> release(ReleasedOrderItemEvent event) {
      return orderItems.stream()
          .map(i -> i.skuId().equals(event.skuId)
              ? release(event, i)
              : i)
          .toList();
    }

    private OrderItem release(ReleasedOrderItemEvent event, OrderItem orderItem) {
      return new OrderItem(
          orderItem.skuId(),
          orderItem.skuName(),
          orderItem.quantity(),
          null,
          null);
    }

    private List<OrderItem> backOrder(BackOrderedOrderItemEvent event) {
      return orderItems.stream()
          .map(i -> i.skuId().equals(event.skuId)
              ? backOrder(event, i)
              : i)
          .toList();
    }

    private OrderItem backOrder(BackOrderedOrderItemEvent event, OrderItem orderItem) {
      return new OrderItem(
          orderItem.skuId(),
          orderItem.skuName(),
          orderItem.quantity(),
          null,
          event.backOrderedAt());
    }
  }

  public interface Event {}

  public record OrderItem(
      String skuId,
      String skuName,
      int quantity,
      Instant readyToShipAt,
      Instant backOrderedAt) {}

  public record CreateShippingOrderCommand(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) {}

  public record CreatedShippingOrderEvent(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) implements Event {}

  public record ReadyToShipOrderItemCommand(String orderId, String skuId, Instant readyToShipAt) {}

  public record ReadyToShipOrderItemEvent(String orderId, String skuId, Instant readyToShipAt) implements Event {}

  public record ReadyToShipOrderEvent(String orderId, Instant readyToShipAt) implements Event {}

  public record ReleaseOrderItemCommand(String orderId, String skuId) {}

  public record ReleasedOrderItemEvent(String orderId, String skuId) implements Event {}

  public record ReleasedOrderEvent(String orderId) implements Event {}

  public record BackOrderOrderItemCommand(String orderId, String skuId, Instant backOrderedAt) {}

  public record BackOrderedOrderItemEvent(String orderId, String skuId, Instant backOrderedAt) implements Event {}

  public record BackOrderedOrderEvent(String orderId, Instant backOrderedAt) implements Event {}
}
