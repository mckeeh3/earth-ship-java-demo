package io.example.shipping;

import java.math.BigDecimal;

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
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;

@EntityKey("orderItemId")
@EntityType("order-item")
@RequestMapping("/order-item/{orderItemId}")
public class OrderItemEntity extends EventSourcedEntity<OrderItemEntity.State, OrderItemEntity.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public OrderItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/create")
  public Effect<String> create(@RequestBody CreateOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderItemId(), "Cannot create Order Item without orderItemId")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/ready-to-ship")
  public Effect<String> readyToShip(@RequestBody ReadyToShipOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/release")
  public Effect<String> release(@RequestBody ReleaseOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/back-order")
  public Effect<String> backOrder(@RequestBody BackOrderOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetOrderItem", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Order Item not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleaseOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String orderItemId,
      String orderId,
      String skuId,
      String skuName,
      int quantity,
      BigDecimal price,
      String status) {

    static State emptyState() {
      return new State(null, null, null, null, 0, null, null);
    }

    boolean isEmpty() {
      return orderItemId == null || orderItemId.isEmpty();
    }

    CreatedOrderItemEvent eventFor(CreateOrderItemCommand command) {
      return new CreatedOrderItemEvent(command.orderItemId(), command.orderId(), command.skuId(), command.skuName(), command.quantity(), command.price());
    }

    ReadyToShipOrderSkuItemEvent eventFor(ReadyToShipOrderSkuItemCommand command) {
      return new ReadyToShipOrderSkuItemEvent(command.orderItemId());
    }

    ReleaseOrderSkuItemEvent eventFor(ReleaseOrderSkuItemCommand command) {
      return new ReleaseOrderSkuItemEvent(command.orderItemId());
    }

    BackOrderOrderSkuItemEvent eventFor(BackOrderOrderSkuItemCommand command) {
      return new BackOrderOrderSkuItemEvent(command.orderItemId());
    }

    State on(CreatedOrderItemEvent event) {
      return new State(event.orderItemId(), event.orderId(), event.skuId(), event.skuName(), event.quantity(), event.price(), "CREATED");
    }

    State on(ReadyToShipOrderSkuItemEvent event) {
      return new State(orderItemId, orderId, skuId, skuName, quantity, price, "READY_TO_SHIP");
    }

    State on(ReleaseOrderSkuItemEvent event) {
      return new State(orderItemId, orderId, skuId, skuName, quantity, price, "RELEASED");
    }

    State on(BackOrderOrderSkuItemEvent event) {
      return new State(orderItemId, orderId, skuId, skuName, quantity, price, "BACK_ORDERED");
    }
  }

  public interface Event {}

  public record CreateOrderItemCommand(String orderItemId, String orderId, String skuId, String skuName, int quantity, BigDecimal price) {}

  public record CreatedOrderItemEvent(String orderItemId, String orderId, String skuId, String skuName, int quantity, BigDecimal price) implements Event {}

  public record ReadyToShipOrderSkuItemCommand(String orderItemId) {}

  public record ReadyToShipOrderSkuItemEvent(String orderItemId) implements Event {}

  public record ReleaseOrderSkuItemCommand(String orderItemId) {}

  public record ReleaseOrderSkuItemEvent(String orderItemId) implements Event {}

  public record BackOrderOrderSkuItemCommand(String orderItemId) {}

  public record BackOrderOrderSkuItemEvent(String orderItemId) implements Event {}
}
