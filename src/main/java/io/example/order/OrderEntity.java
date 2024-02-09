package io.example.order;

import java.math.BigDecimal;
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
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;

@Id("orderId")
@TypeId("order")
@RequestMapping("/order/{orderId}")
public class OrderEntity extends EventSourcedEntity<OrderEntity.State, OrderEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(OrderEntity.class);
  private final String entityId;

  public OrderEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/create")
  public Effect<String> createOrder(@RequestBody CreateOrderCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isEmpty(command.orderId(), "Cannot create order without order id")
        .isEmpty(command.customerId(), "Cannot create order without customer id")
        .isEmpty(command.orderItems(), "Cannot create order without items")
        .onSuccess(() -> effects()
            .emitEvents(currentState().eventsFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @PatchMapping("/order-item-update")
  public Effect<String> orderItemUpdate(@RequestBody OrderItemUpdateCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/deliver")
  public Effect<String> deliverOrder(@RequestBody DeliverOrderCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isEmpty(command.orderId(), "Cannot deliver order without order id")
        .isNull(currentState().readyToShipAt(), "Cannot deliver order without shipping")
        .isNotNull(currentState().canceledAt, "Cannot deliver canceled order")
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @PatchMapping("/return")
  public Effect<String> returnOrder(@RequestBody ReturnOrderCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isEmpty(command.orderId(), "Cannot return order without order id")
        .isNull(currentState().readyToShipAt(), "Cannot return order without shipping")
        .isNull(currentState().deliveredAt, "Cannot return order without delivery")
        .isNotNull(currentState().canceledAt, "Cannot return canceled order")
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @PatchMapping("/cancel")
  public Effect<String> cancelOrder(@RequestBody CancelOrderCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isEmpty(command.orderId(), "Cannot cancel order without order id")
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @GetMapping
  public Effect<State> get() {
    log.info("C-EntityId: {}\n_State: {}\n_Get", entityId, currentState());
    return Validator
        .isTrue(currentState().isEmpty(), "Order is not found")
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @EventHandler
  public State on(CreatedOrderEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemUpdatedEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(DeliveredOrderEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReturnedOrderEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CanceledOrderEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String orderId,
      String customerId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt,
      Instant deliveredAt,
      Instant returnedAt,
      Instant canceledAt,
      List<OrderItem> orderItems) {

    static State emptyState() {
      return new State("", "", null, null, null, null, null, null, List.of());
    }

    boolean isEmpty() {
      return orderId.isEmpty();
    }

    List<Event> eventsFor(CreateOrderCommand command) {
      if (!isEmpty()) {
        return List.of();
      }
      return List.of(new CreatedOrderEvent(command.orderId(), command.customerId(), Instant.now(), command.orderItems()));
    }

    List<Event> eventsFor(OrderItemUpdateCommand command) {
      var newOrderItems = orderItems.stream()
          .map(i -> i.skuId.equals(command.skuId())
              ? new OrderItem(
                  i.skuId(),
                  i.skuName(),
                  i.skuDescription(),
                  i.skuPrice(),
                  i.quantity(),
                  command.readyToShipAt(),
                  command.backOrderedAt())
              : i)
          .toList();
      var newBackOrderedAt = newOrderItems.stream().anyMatch(i -> i.backOrderedAt() != null) ? Instant.now() : null;
      var newReadyToShipAt = newBackOrderedAt == null && newOrderItems.stream().allMatch(i -> i.readyToShipAt() != null) ? Instant.now() : null;

      var event = new OrderItemUpdatedEvent(command.orderId(), command.skuId(), newReadyToShipAt, newBackOrderedAt, newOrderItems);

      return command.backOrderedAt() != null
          ? List.of(event, new BackOrderedOrderEvent(command.orderId(), newBackOrderedAt))
          : newReadyToShipAt != null
              ? List.of(event, new ReadyToShipOrderEvent(command.orderId(), newReadyToShipAt))
              : List.of(event);
    }

    Event eventFor(DeliverOrderCommand command) {
      return new DeliveredOrderEvent(command.orderId());
    }

    Event eventFor(ReturnOrderCommand command) {
      return new ReturnedOrderEvent(command.orderId());
    }

    Event eventFor(CancelOrderCommand command) {
      return new CanceledOrderEvent(command.orderId());
    }

    State on(CreatedOrderEvent event) {
      return new State(
          event.orderId(),
          event.customerId(),
          Instant.now(),
          readyToShipAt,
          backOrderedAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          event.orderItems());
    }

    State on(ReadyToShipOrderEvent event) {
      return this;
    }

    State on(BackOrderedOrderEvent event) {
      return this;
    }

    State on(OrderItemUpdatedEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          event.orderReadyToShipAt(),
          event.orderBackOrderedAt(),
          deliveredAt,
          returnedAt,
          canceledAt,
          event.orderItems());
    }

    State on(DeliveredOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          Instant.now(),
          returnedAt,
          canceledAt,
          orderItems);
    }

    State on(ReturnedOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          deliveredAt,
          Instant.now(),
          canceledAt,
          orderItems);
    }

    State on(CanceledOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          deliveredAt,
          returnedAt,
          Instant.now(),
          orderItems);
    }
  }

  public record OrderItem(String skuId, String skuName, String skuDescription, BigDecimal skuPrice, int quantity, Instant readyToShipAt, Instant backOrderedAt) {}

  public interface Event {}

  public record CreateOrderCommand(String orderId, String customerId, List<OrderItem> orderItems) {}

  public record CreatedOrderEvent(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) implements Event {}

  public record OrderItemUpdateCommand(String orderId, String skuId, Instant readyToShipAt, Instant backOrderedAt) {};

  public record OrderItemUpdatedEvent(String orderId, String skuId, Instant orderReadyToShipAt, Instant orderBackOrderedAt, List<OrderItem> orderItems) implements Event {}

  public record ReadyToShipOrderEvent(String orderId, Instant readyToShipAt) implements Event {}

  public record BackOrderedOrderEvent(String orderId, Instant backOrderedAt) implements Event {}

  public record DeliverOrderCommand(String orderId) {}

  public record DeliveredOrderEvent(String orderId) implements Event {}

  public record ReturnOrderCommand(String orderId) {}

  public record ReturnedOrderEvent(String orderId) implements Event {}

  public record CancelOrderCommand(String orderId) {}

  public record CanceledOrderEvent(String orderId) implements Event {}
}
