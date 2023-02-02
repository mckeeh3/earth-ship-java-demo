package io.example.stock;

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

@EntityKey("productId")
@EntityType("product")
@RequestMapping("/cart/{productId}")
public class ProductEntity extends EventSourcedEntity<ProductEntity.State> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public ProductEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  // TODO: step 6: define entity commands and request handlers
  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateProductCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.productId(), "Cannot create Product without productId")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetProduct", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Product not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  // TODO: step 5: define entity event handlers
  @EventHandler
  public State on(CreatedProductEvent event) {
    return currentState().on(event);
  }

  // TODO: step 4: define entity state fields
  public record State(String productId) {

    static State emptyState() {
      return new State(null);
    }

    boolean isEmpty() {
      return productId == null || productId.isEmpty();
    }

    // TODO: step 3: define state eventFor methods
    CreatedProductEvent eventFor(CreateProductCommand command) {
      return new CreatedProductEvent(command.productId());
    }

    // TODO: step 2: define state event handler methods
    State on(CreatedProductEvent event) {
      return new State(event.productId);
    }
  }

  // TODO: step 1: define command and event records

  public record CreateProductCommand(String productId) {}

  public record CreatedProductEvent(String productId) {}
}
