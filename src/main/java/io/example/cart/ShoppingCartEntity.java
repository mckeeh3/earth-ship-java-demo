package io.example.cart;

import java.time.Instant;
import java.util.ArrayList;
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

@EntityKey("customerId")
@EntityType("shopping-cart")
@RequestMapping("/cart/{customerId}")
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCartEntity.State> {
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
    log.info("EntityId: {}\nState {}\nCommand: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.customerId(), "Cannot add item to cart without customer id")
        .isEmpty(command.skuId(), "Cannot add item to cart without sku id")
        .isEmpty(command.skuName(), "Cannot add item to cart without sku name")
        .isLtEqZero(command.quantity(), "Cannot add item to cart with quantity <= 0")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/items/{sku_id}/change")
  public Effect<String> changeLineItem(@RequestBody ChangeLineItemCommand command) {
    log.info("EntityId: {}\nState {}\nCommand: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isNull(currentState().findLineItem(command.skuId), "Item not found in cart")
        .isEmpty(currentState().lineItems, "Cannot change item in empty cart")
        .isEmpty(command.skuId(), "Cannot change item in cart without sku id")
        .isLtEqZero(command.quantity(), "Cannot change item in cart with quantity <= 0")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/items/{sku_id}/remove")
  public Effect<String> removeLineItem(@RequestBody RemoveLineItemCommand command) {
    log.info("EntityId: {}\nState {}\nCommand: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.skuId(), "Cannot remove item from cart without sku id")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/checkout")
  public Effect<String> checkout(@RequestBody CheckoutCommand command) {
    log.info("EntityId: {}\nState {}\nCommand: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(currentState().lineItems, "Cannot checkout empty cart")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @GetMapping()
  public Effect<State> get() {
    log.info("EntityId: {}\nState {}\nGetShoppingCart", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Shopping cart is not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(AddedLineItemEvent event) {
    log.info("EntityId: {}\nState {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ChangedLineItemEvent event) {
    log.info("EntityId: {}\nState {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(RemovedLineItemEvent event) {
    log.info("EntityId: {}\nState {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CheckedOutEvent event) {
    log.info("EntityId: {}\nState {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String customerId,
      List<LineItem> lineItems) {

    static State emptyState() {
      return new State("", List.of());
    }

    boolean isEmpty() {
      return customerId.isEmpty();
    }

    AddedLineItemEvent eventFor(AddLineItemCommand command) {
      return new AddedLineItemEvent(command.customerId, command.skuId(), command.skuName, command.quantity);
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
      var newItems = new ArrayList<LineItem>(lineItems.stream().filter(i -> !i.skuId().equals(event.skuId)).toList());
      newItems.add(new LineItem(event.skuId, event.skuName, event.quantity));
      return new State(
          event.customerId,
          newItems);
    }

    State on(ChangedLineItemEvent event) {
      var newItems = new ArrayList<LineItem>(lineItems.stream().filter(i -> !i.skuId().equals(event.skuId)).toList());
      newItems.add(new LineItem(event.skuId, findLineItem(event.skuId).skuName(), event.quantity));
      return new State(
          customerId,
          newItems);
    }

    State on(RemovedLineItemEvent event) {
      var newItems = new ArrayList<LineItem>(lineItems.stream().filter(i -> !i.skuId().equals(event.skuId)).toList());
      return new State(
          customerId,
          newItems);
    }

    State on(CheckedOutEvent event) {
      return new State(
          customerId,
          List.of());
    }

    LineItem findLineItem(String skuId) {
      return lineItems.stream()
          .filter(i -> i.skuId().equals(skuId))
          .findFirst()
          .orElse(null);
    }
  }

  public record LineItem(String skuId, String skuName, int quantity) {}

  public record AddLineItemCommand(String customerId, String skuId, String skuName, int quantity) {}

  public record AddedLineItemEvent(String customerId, String skuId, String skuName, int quantity) {}

  public record ChangeLineItemCommand(String customerId, String skuId, int quantity) {}

  public record ChangedLineItemEvent(String customerId, String skuId, int quantity) {}

  public record RemoveLineItemCommand(String customerId, String skuId) {}

  public record RemovedLineItemEvent(String customerId, String skuId) {}

  public record CheckoutCommand(String customerId) {}

  public record CheckedOutEvent(String customerId, Instant checkOutAt, List<LineItem> items) {}
}
