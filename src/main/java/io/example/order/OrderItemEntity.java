package io.example.order;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.grpc.Status;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.valueentity.ValueEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;

@EntityKey("orderItemId")
@EntityType("order-item")
@RequestMapping("/order-item/{orderItemId}")
public class OrderItemEntity extends ValueEntity<OrderItemEntity.State> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public OrderItemEntity(ValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isNull(command.orderItemId(), "Cannot create OrderItem without orderItemId")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .updateState(currentState().on(command))
            .thenReply("OK"));
  }

  @PutMapping("/update")
  public Effect<String> update(@RequestBody UpdateOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isFalse(currentState().isEmpty(), "OrderItem not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .updateState(currentState().on(command))
            .thenReply("OK"));
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetOrderItem", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "OrderItem not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  public record State(
      OrderItemId orderItemId,
      String customerId,
      String skuId,
      String skuName,
      int quantity,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt) {

    static State emptyState() {
      return new State(null, null, null, null, 0, null, null, null);
    }

    boolean isEmpty() {
      return orderItemId == null;
    }

    State on(CreateOrderItemCommand command) {
      return new State(
          command.orderItemId,
          command.customerId,
          command.skuId,
          command.skuName,
          command.quantity,
          command.orderedAt,
          null,
          null);
    }

    State on(UpdateOrderItemCommand command) {
      return new State(
          orderItemId,
          customerId,
          skuId,
          skuName,
          quantity,
          orderedAt,
          command.readyToShipAt,
          command.backOrderedAt);
    }
  }

  public record CreateOrderItemCommand(
      OrderItemId orderItemId,
      String customerId,
      String skuId,
      String skuName,
      int quantity,
      Instant orderedAt) {}

  public record UpdateOrderItemCommand(OrderItemId orderItemId, Instant readyToShipAt, Instant backOrderedAt) {}
}
