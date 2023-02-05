package io.example.product;

import java.math.BigDecimal;

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

@EntityKey("skuId")
@EntityType("product")
@RequestMapping("/product/{skuId}")
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

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateProductCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.skuId(), "Cannot create Product without skuId")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/update-available")
  public Effect<String> updateAvailable(@RequestBody UpdateProductsAvailableCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/update-back-ordered")
  public Effect<String> updateBackOrdered(@RequestBody UpdateProductsBackOrderedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetProduct", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Product not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedProductEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedProductsAvailableEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedProductsBackOrderedEvent event) {
    return currentState().on(event);
  }

  public record State(
      String skuId,
      String skuName,
      String skuDescription,
      int available,
      int backOrdered,
      BigDecimal skuPrice) {

    static State emptyState() {
      return new State(null, null, null, 0, 0, null);
    }

    boolean isEmpty() {
      return skuId == null || skuId.isEmpty();
    }

    CreatedProductEvent eventFor(CreateProductCommand command) {
      return new CreatedProductEvent(
          command.skuId(),
          command.skuNAme(),
          command.skuDescription(),
          command.skuPrice());
    }

    UpdatedProductsAvailableEvent eventFor(UpdateProductsAvailableCommand command) {
      return new UpdatedProductsAvailableEvent(command.skuId(), command.available());
    }

    UpdatedProductsBackOrderedEvent eventFor(UpdateProductsBackOrderedCommand command) {
      return new UpdatedProductsBackOrderedEvent(command.skuId(), command.backOrdered());
    }

    State on(CreatedProductEvent event) {
      return new State(
          event.skuId(),
          event.skuNAme(),
          event.skuDescription(),
          isEmpty() ? 0 : available,
          isEmpty() ? 0 : backOrdered,
          event.skuPrice());
    }

    State on(UpdatedProductsAvailableEvent event) {
      return new State(
          skuId,
          skuName,
          skuDescription,
          event.available(),
          backOrdered,
          skuPrice);
    }

    State on(UpdatedProductsBackOrderedEvent event) {
      return new State(
          skuId,
          skuName,
          skuDescription,
          available,
          event.backOrdered(),
          skuPrice);
    }
  }

  public record CreateProductCommand(String skuId, String skuNAme, String skuDescription, BigDecimal skuPrice) {}

  public record CreatedProductEvent(String skuId, String skuNAme, String skuDescription, BigDecimal skuPrice) {}

  public record UpdateProductsAvailableCommand(String skuId, int available) {}

  public record UpdatedProductsAvailableEvent(String skuId, int available) {}

  public record UpdateProductsBackOrderedCommand(String skuId, int backOrdered) {}

  public record UpdatedProductsBackOrderedEvent(String skuId, int backOrdered) {}
}
