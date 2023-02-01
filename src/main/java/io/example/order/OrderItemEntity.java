package io.example.order;

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
@RequestMapping("/cart/{orderItemId}")
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

  // TODO: step 4: define entity commands and request handlers
  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateOrderItem2Command command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.orderItemId(), "Cannot create OrderItem2 without orderItemId")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .updateState(currentState().on(command))
            .thenReply("OK"));
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetOrderItem2", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "OrderItem2 not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  // TODO: step 3: define entity state fields
  public record State(String orderItemId) {

    static State emptyState() {
      return new State(null);
    }

    boolean isEmpty() {
      return orderItemId == null || orderItemId.isEmpty();
    }

    // TODO: step 2: define command handler methods
    State on(CreateOrderItem2Command command) {
      return new State(command.orderItemId);
    }
  }

  // TODO: step 1: define command records

  public record CreateOrderItem2Command(String orderItemId) {}
}
