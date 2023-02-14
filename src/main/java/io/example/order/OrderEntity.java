package io.example.order;

import java.math.BigDecimal;
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

  @PutMapping("/create")
  public Effect<String> createOrder(@RequestBody CreateOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot create order without order id")
        .isEmpty(command.customerId(), "Cannot create order without customer id")
        .isEmpty(command.orderItems(), "Cannot create order without items")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/ready-to-ship")
  public Effect<String> shipOrder(@RequestBody ReadyToShipOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot ship order without order id")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/release")
  public Effect<String> releaseOrder(@RequestBody ReleaseOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot release order without order id")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/back-order")
  public Effect<String> backOrder(@RequestBody BackOrderOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot back order without order id")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/ready-to-ship-order-item")
  public Effect<String> shipOrderSku(@RequestBody ReadyToShipOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
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
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot release order sku without order id")
        .isEmpty(command.skuId(), "Cannot release order sku without sku")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/back-order-order-item")
  public Effect<String> backOrderSku(@RequestBody BackOrderOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot back order sku without order id")
        .isEmpty(command.skuId(), "Cannot back order sku without sku")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/deliver")
  public Effect<String> deliverOrder(@RequestBody DeliverOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
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
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
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
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderId(), "Cannot cancel order without order id")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @GetMapping
  public Effect<State> getOrder() {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), "GetOrder");
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Order is not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(DeliveredOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReturnedOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CanceledOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
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

    CreatedOrderEvent eventFor(CreateOrderCommand command) {
      var newOrderedAt = orderedAt == null ? Instant.now() : orderedAt;
      return new CreatedOrderEvent(command.orderId(), command.customerId(), newOrderedAt, command.orderItems());
    }

    ReadyToShipOrderEvent eventFor(ReadyToShipOrderCommand command) {
      return new ReadyToShipOrderEvent(command.orderId(), command.readyToShipAt());
    }

    ReleasedOrderEvent eventFor(ReleaseOrderCommand command) {
      return new ReleasedOrderEvent(command.orderId());
    }

    BackOrderedOrderEvent eventFor(BackOrderOrderCommand command) {
      return new BackOrderedOrderEvent(command.orderId(), command.backOrderedAt());
    }

    ReadyToShipOrderItemEvent eventFor(ReadyToShipOrderItemCommand command) {
      return new ReadyToShipOrderItemEvent(command.orderId(), command.skuId(), command.readyToShipAt());
    }

    ReleasedOrderItemEvent eventFor(ReleaseOrderItemCommand command) {
      return new ReleasedOrderItemEvent(command.orderId(), command.skuId());
    }

    BackOrderedOrderItemEvent eventFor(BackOrderOrderItemCommand command) {
      return new BackOrderedOrderItemEvent(command.orderId(), command.skuId(), command.backOrderedAt(), true);
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
          backOrderedAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          event.orderItems());
    }

    State on(ReadyToShipOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          event.readyToShipAt(),
          null,
          deliveredAt,
          returnedAt,
          canceledAt,
          orderItems);
    }

    State on(ReleasedOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          null,
          null,
          deliveredAt,
          returnedAt,
          canceledAt,
          orderItems);
    }

    State on(BackOrderedOrderEvent event) {
      return new State(
          orderId,
          customerId,
          orderedAt,
          null,
          event.backOrderedAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          orderItems);
    }

    State on(ReadyToShipOrderItemEvent event) {
      var newOrderItems = orderItems.stream()
          .map(i -> {
            if (i.skuId.equals(event.skuId())) {
              return new OrderItem(i.skuId, i.skuName, i.skuDescription, i.skuPrice, i.quantity, event.readyToShipAt(), null);
            } else {
              return i;
            }
          }).toList();
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          newOrderItems);
    }

    State on(ReleasedOrderItemEvent event) {
      var newOrderItems = orderItems.stream()
          .map(i -> {
            if (i.skuId.equals(event.skuId())) {
              return new OrderItem(i.skuId, i.skuName, i.skuDescription, i.skuPrice, i.quantity, null, null);
            } else {
              return i;
            }
          }).toList();
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          newOrderItems);
    }

    State on(BackOrderedOrderItemEvent event) {
      var newOrderItems = orderItems.stream()
          .map(i -> {
            if (i.skuId.equals(event.skuId())) {
              return new OrderItem(i.skuId, i.skuName, i.skuDescription, i.skuPrice, i.quantity, null, event.backOrderedAt);
            } else {
              return i;
            }
          }).toList();
      return new State(
          orderId,
          customerId,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          deliveredAt,
          returnedAt,
          canceledAt,
          newOrderItems);
    };

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

  public record CreateOrderCommand(String orderId, String customerId, List<OrderItem> orderItems) {}

  public record CreatedOrderEvent(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) {}

  public record ReadyToShipOrderCommand(String orderId, Instant readyToShipAt) {}

  public record ReadyToShipOrderEvent(String orderId, Instant readyToShipAt) {}

  public record ReleaseOrderCommand(String orderId) {}

  public record ReleasedOrderEvent(String orderId) {}

  public record BackOrderOrderCommand(String orderId, Instant backOrderedAt) {}

  public record BackOrderedOrderEvent(String orderId, Instant backOrderedAt) {}

  public record ReadyToShipOrderItemCommand(String orderId, String skuId, Instant readyToShipAt) {}

  public record ReadyToShipOrderItemEvent(String orderId, String skuId, Instant readyToShipAt) {}

  public record ReleaseOrderItemCommand(String orderId, String skuId) {}

  public record ReleasedOrderItemEvent(String orderId, String skuId) {}

  public record BackOrderOrderItemCommand(String orderId, String skuId, Instant backOrderedAt) {}

  public record BackOrderedOrderItemEvent(String orderId, String skuId, Instant backOrderedAt, boolean test) {}

  public record DeliverOrderCommand(String orderId) {}

  public record DeliveredOrderEvent(String orderId) {}

  public record ReturnOrderCommand(String orderId) {}

  public record ReturnedOrderEvent(String orderId) {}

  public record CancelOrderCommand(String orderId) {}

  public record CanceledOrderEvent(String orderId) {}
}
