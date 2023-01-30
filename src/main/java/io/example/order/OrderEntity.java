package io.example.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("orderId")
@EntityType("order")
@RequestMapping("/order/{orderId}")
public class OrderEntity extends EventSourcedEntity<OrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(OrderEntity.class);
  private final String entityId;

  public OrderEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/create")
  public Effect<String> createOrder(@RequestBody CreateOrderCommand command) {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot create order without order id")
        .isEmpty(command.customerId(), "Cannot create order without customer id")
        .isEmpty(command.items(), "Cannot create order without items")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/ready-to-ship")
  public Effect<String> shipOrder(@RequestBody ReadyToShipOrderCommand command) {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot ship order without order id")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/release")
  public Effect<String> releaseOrder(@RequestBody ReleaseOrderCommand command) {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot release order without order id")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/ready-to-ship-order-item")
  public Effect<String> shipOrderSku(@RequestBody ReadyToShipOrderItemCommand command) {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot ship order sku without order id")
        .isEmpty(command.skuId(), "Cannot ship order sku without sku")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/release-order-item")
  public Effect<String> releaseOrderSku(@RequestBody ReleaseOrderItemCommand command) {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot release order sku without order id")
        .isEmpty(command.skuId(), "Cannot release order sku without sku")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/deliver")
  public Effect<String> deliverOrder(@RequestBody DeliverOrderCommand command) {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot deliver order without order id")
        .isNull(currentState().readyToShipAt(), "Cannot deliver order without shipping")
        .isNotNull(currentState().canceledAt, "Cannot deliver canceled order")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/return")
  public Effect<String> returnOrder(@RequestBody ReturnOrderCommand command) {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot return order without order id")
        .isNull(currentState().readyToShipAt(), "Cannot return order without shipping")
        .isNull(currentState().deliveredAt, "Cannot return order without delivery")
        .isNotNull(currentState().canceledAt, "Cannot return canceled order")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/cancel")
  public Effect<String> cancelOrder(@RequestBody CancelOrderCommand command) {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot cancel order without order id")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @GetMapping
  public Effect<State> getOrder() {
    log.info("EntityId: {}\nState {}\n_Command: {}", entityId, currentState(), "GetOrder");
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Order is not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedOrderEvent event) {
    log.info("EntityId: {}\nState {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderEvent event) {
    log.info("EntityId: {}\nState {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderEvent event) {
    log.info("EntityId: {}\nState {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderItemEvent event) {
    log.info("EntityId: {}\nState {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderItemEvent event) {
    log.info("EntityId: {}\nState {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(DeliveredOrderEvent event) {
    log.info("EntityId: {}\nState {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReturnedOrderEvent event) {
    log.info("EntityId: {}\nState {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CanceledOrderEvent event) {
    log.info("EntityId: {}\nState {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String orderId,
      String customerId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant deliveredAt,
      Instant returnedAt,
      Instant canceledAt,
      List<OrderItem> items) {

    static State emptyState() {
      return new State("", "", null, null, null, null, null, List.of());
    }

    boolean isEmpty() {
      return orderId.isEmpty();
    }

    CreatedOrderEvent eventFor(CreateOrderCommand command) {
      var newOrderedAt = orderedAt == null ? Instant.now() : orderedAt;
      return new CreatedOrderEvent(command.orderId(), command.customerId(), newOrderedAt, command.items());
    }

    ReadyToShipOrderEvent eventFor(ReadyToShipOrderCommand command) {
      return new ReadyToShipOrderEvent(command.orderId(), command.readyToShipAt());
    }

    ReleasedOrderEvent eventFor(ReleaseOrderCommand command) {
      return new ReleasedOrderEvent(command.orderId());
    }

    ReadyToShipOrderItemEvent eventFor(ReadyToShipOrderItemCommand command) {
      return new ReadyToShipOrderItemEvent(command.orderId(), command.skuId(), command.readyToShipAt());
    }

    ReleasedOrderItemEvent eventFor(ReleaseOrderItemCommand command) {
      return new ReleasedOrderItemEvent(command.orderId(), command.skuId());
    }

    DeliveredOrderEvent eventFor(DeliverOrderCommand command) {
      return new DeliveredOrderEvent(command.orderId());
    }

    ReturnedOrderEvent eventFor(ReturnOrderCommand command) {
      return new ReturnedOrderEvent(command.orderId());
    }

    CanceledOrderEvent eventFor(CancelOrderCommand command) {
      return new CanceledOrderEvent(command.orderId());
    }

    State on(CreatedOrderEvent event) {
      return new State(
          event.orderId(),
          event.customerId(),
          Instant.now(),
          readyToShipAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          event.items());
    }

    State on(ReadyToShipOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          Instant.now(),
          deliveredAt,
          returnedAt,
          canceledAt,
          items);
    }

    State on(ReleasedOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          null,
          deliveredAt,
          returnedAt,
          canceledAt,
          items);
    }

    State on(ReadyToShipOrderItemEvent event) {
      var item = findItem(event.skuId());
      var newItems = new ArrayList<OrderItem>(items.stream().filter(i -> !i.skuId.equals(event.skuId)).toList());
      if (item != null) {
        newItems.add(new OrderItem(item.skuId, item.skuName, item.quantity, Instant.now()));
      }
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          newItems);
    }

    State on(ReleasedOrderItemEvent event) {
      var item = findItem(event.skuId());
      var newItems = new ArrayList<OrderItem>(items.stream().filter(i -> !i.skuId.equals(event.skuId)).toList());
      if (item != null) {
        newItems.add(new OrderItem(item.skuId, item.skuName, item.quantity, null));
      }
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          newItems);
    }

    State on(DeliveredOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          Instant.now(),
          returnedAt,
          canceledAt,
          items);
    }

    State on(ReturnedOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          deliveredAt,
          Instant.now(),
          canceledAt,
          items);
    }

    State on(CanceledOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          deliveredAt,
          returnedAt,
          Instant.now(),
          items);
    }

    OrderItem findItem(String skuId) {
      return items.stream()
          .filter(item -> item.skuId().equals(skuId))
          .findFirst()
          .orElse(null);
    }
  }

  public record OrderItem(String skuId, String skuName, int quantity, Instant readyToShipAt) {}

  public record CreateOrderCommand(String orderId, String customerId, List<OrderItem> items) {}

  public record CreatedOrderEvent(String orderId, String customerId, Instant orderedAt, List<OrderItem> items) {}

  public record ReadyToShipOrderCommand(String orderId, Instant readyToShipAt) {}

  public record ReadyToShipOrderEvent(String orderId, Instant readyToShipAt) {}

  public record ReleaseOrderCommand(String orderId) {}

  public record ReleasedOrderEvent(String orderId) {}

  public record ReadyToShipOrderItemCommand(String orderId, String skuId, Instant readyToShipAt) {}

  public record ReadyToShipOrderItemEvent(String orderId, String skuId, Instant readyToShipAt) {}

  public record ReleaseOrderItemCommand(String orderId, String skuId) {}

  public record ReleasedOrderItemEvent(String orderId, String skuId) {}

  public record DeliverOrderCommand(String orderId) {}

  public record DeliveredOrderEvent(String orderId) {}

  public record ReturnOrderCommand(String orderId) {}

  public record ReturnedOrderEvent(String orderId) {}

  public record CancelOrderCommand(String orderId) {}

  public record CanceledOrderEvent(String orderId) {}
}
