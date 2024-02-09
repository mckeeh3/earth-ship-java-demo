package io.example.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

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
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;

@Id("customerId")
@TypeId("shoppingCart")
@RequestMapping("/cart/{customerId}")
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCartEntity.State, ShoppingCartEntity.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public ShoppingCartEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/items/add")
  public Effect<String> addLineItem(@RequestBody AddLineItemCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isEmpty(command.customerId(), "Cannot add item to cart without customer id")
        .isEmpty(command.skuId(), "Cannot add item to cart without sku id")
        .isEmpty(command.skuName(), "Cannot add item to cart without sku name")
        .isLtEqZero(command.quantity(), "Cannot add item to cart with quantity <= 0")
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @PutMapping("/items/{sku_id}/change")
  public Effect<String> changeLineItem(@RequestBody ChangeLineItemCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isFalse(currentState().containsLineItem(command.skuId), "Item '%s' not found in cart".formatted(command.skuId))
        .isEmpty(currentState().lineItems, "Cannot change item in empty cart")
        .isEmpty(command.skuId(), "Cannot change item in cart without sku id")
        .isLtEqZero(command.quantity(), "Cannot change item in cart with quantity <= 0")
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @PutMapping("/items/{sku_id}/remove")
  public Effect<String> removeLineItem(@RequestBody RemoveLineItemCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isEmpty(command.skuId(), "Cannot remove item from cart without sku id")
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @PutMapping("/checkout")
  public Effect<String> checkout(@RequestBody CheckoutCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isEmpty(currentState().lineItems, "Cannot checkout empty cart")
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @GetMapping()
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_Get", entityId, currentState());
    return Validator
        .isTrue(currentState().isEmpty(), "Shopping cart is not found")
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @EventHandler
  public State on(AddedLineItemEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ChangedLineItemEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(RemovedLineItemEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CheckedOutEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String customerId,
      Instant createdAt,
      List<LineItem> lineItems) {

    static State emptyState() {
      return new State("", Instant.now(), List.of());
    }

    boolean isEmpty() {
      return customerId.isEmpty();
    }

    AddedLineItemEvent eventFor(AddLineItemCommand command) {
      return new AddedLineItemEvent(command.customerId, command.skuId(), command.skuName, command.skuDescription, command.skuPrice, command.quantity);
    }

    ChangedLineItemEvent eventFor(ChangeLineItemCommand command) {
      return new ChangedLineItemEvent(command.customerId, command.skuId(), command.quantity);
    }

    RemovedLineItemEvent eventFor(RemoveLineItemCommand command) {
      return new RemovedLineItemEvent(command.customerId, command.skuId());
    }

    CheckedOutEvent eventFor(CheckoutCommand command) {
      return new CheckedOutEvent(customerId, Instant.now(), lineItems);
    }

    State on(AddedLineItemEvent event) {
      var newLineItems = Stream.concat(
          lineItems.stream().filter(i -> !i.skuId().equals(event.skuId)),
          Stream.of(new LineItem(event.skuId, event.skuName, event.skuDescription, event.skuPrice, event.quantity)))
          .toList();
      return new State(
          event.customerId,
          createdAt,
          newLineItems);
    }

    State on(ChangedLineItemEvent event) {
      var newLineItems = lineItems.stream()
          .map(i -> {
            if (i.skuId().equals(event.skuId)) {
              return new LineItem(i.skuId, i.skuName(), i.skuDescription, i.skuPrice, event.quantity);
            } else {
              return i;
            }
          }).toList();
      return new State(
          customerId,
          createdAt,
          newLineItems);
    }

    State on(RemovedLineItemEvent event) {
      var newLineItems = lineItems.stream().filter(i -> !i.skuId().equals(event.skuId)).toList();
      return new State(
          customerId,
          createdAt,
          newLineItems);
    }

    State on(CheckedOutEvent event) {
      return new State(
          customerId,
          createdAt,
          List.of());
    }

    boolean containsLineItem(String skuId) {
      return lineItems.stream().anyMatch(i -> i.skuId().equals(skuId));
    }
  }

  public interface Event {}

  public record LineItem(String skuId, String skuName, String skuDescription, BigDecimal skuPrice, int quantity) {}

  public record AddLineItemCommand(String customerId, String skuId, String skuName, String skuDescription, BigDecimal skuPrice, int quantity) {}

  public record AddedLineItemEvent(String customerId, String skuId, String skuName, String skuDescription, BigDecimal skuPrice, int quantity) implements Event {}

  public record ChangeLineItemCommand(String customerId, String skuId, int quantity) {}

  public record ChangedLineItemEvent(String customerId, String skuId, int quantity) implements Event {}

  public record RemoveLineItemCommand(String customerId, String skuId) {}

  public record RemovedLineItemEvent(String customerId, String skuId) implements Event {}

  public record CheckoutCommand(String customerId) {}

  public record CheckedOutEvent(String customerId, Instant checkOutAt, List<LineItem> items) implements Event {}
}
