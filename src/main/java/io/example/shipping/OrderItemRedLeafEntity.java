package io.example.shipping;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.shipping.OrderItemRedTreeEntity.OrderItemRedTreeId;
import io.example.stock.StockOrderRedLeafEntity;
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

// ===========================================================
// ===== Processing flow when order item leaf is created =====
// ===========================================================
//
// Entity: OrderItemRedLeafEntity
//
// OrderItemCreateCommand
// --> OrderItemCreatedEvent
// --> OrderItemRequestsStockSkuItemsEvent
//
// Action: OrderItemRedLeafEntityToStockOrderRedLeafEntity
//
// OrderItemRequestsStockSkuItemsEvent --> OrderItemRequestsStockSkuItemsCommand
//
// Entity: StockOrderRedLeafEntity
//
// OrderItemRequestsStockSkuItemsCommand
// --> OrderItemConsumedStockSkuItemsEvent
//
// Action: StockOrderRedLeafEntityToOrderItemRedLeafEntity
//
// OrderItemConsumedStockSkuItemsEvent --> OrderItemConsumedStockSkuItemsCommand
//
// Entity: OrderItemRedLeafEntity
//
// OrderItemConsumedStockSkuItemsCommand
// --> OrderItemConsumedStockSkuItemsEvent
// --> OrderItemRequestsStockSkuItemsEvent (optional if more stockSkuItems are needed)
//
// =======================================================================================
// ===== Processing flow when consumed (above) orderSkuItems are no longer available =====
// ===== Consumed here means that the orderSkuItems have consumed stockSkuItems _____=====
// ===== The consumed stockSkuItems are release, which will make them available _____=====
// =======================================================================================
//
// Entity: OrderItemRedLeafEntity
//
// OrderItemConsumedStockSkuItemsCommand
// --> OrderItemReleasedStockSkuItemsEvent (release the stockSkuItems)
// --> OrderItemRequestsStockSkuItemsEvent (optional if more stockSkuItems are needed)
//
// Action: OrderItemRedLeafEntityToStockOrderRedLeafEntity
//
// OrderItemReleasedStockSkuItemsEvent --> OrderItemReleaseStockSkuItemsCommand
//
// Entity: StockOrderRedLeafEntity
//
// OrderItemReleaseStockSkuItemsCommand
// --> OrderItemReleasedStockSkuItemsEvent
// --> OrderItemRequestsStockSkuItemsEvent
//
// ============================================================
// ===== Processing flow when stock order leaf is created =====
// ============================================================
//
// Entity: StockOrderRedLeafEntity
//
// StockOrderRedLeafCreateCommand
// --> StockOrderRedLeafCreatedEvent
// --> StockOrderRequestsOrderSkuItemsEvent
//
// Action: StockOrderRedLeafEntityToOrderItemRedLeafEntity
//
// StockOrderRequestsOrderSkuItemsEvent --> StockOrderRequestsOrderSkuItemsCommand
//
// Entity: OrderItemRedLeafEntity
//
// StockOrderRequestsOrderSkuItemsCommand
// --> StockOrderConsumedOrderSkuItemsEvent
//
// Action: OrderItemRedLeafEntityToStockOrderRedLeafEntity
//
// StockOrderConsumedOrderSkuItemsEvent --> StockOrderConsumedOrderSkuItemsCommand
//
// Entity: StockOrderRedLeafEntity
//
// StockOrderConsumedOrderSkuItemsCommand
// --> StockOrderConsumedOrderSkuItemsEvent
// --> StockOrderRequestsOrderSkuItemsEvent (optional if more orderSkuItems are needed)
//
// =======================================================================================
// ===== Processing flow when consumed (above) stockSkuItems are no longer available =====
// ===== Consumed here means that the stockSkuItems have consumed orderSkuItems _____=====
// ===== The consumed orderSkuItems are release, which will make them available _____=====
// =======================================================================================
//
// Entity: StockOrderRedLeafEntity
//
// StockOrderConsumedOrderSkuItemsCommand
// --> StockOrderReleasedOrderSkuItemsEvent (release the orderSkuItems)
// --> StockOrderRequestsOrderSkuItemsEvent (optional if more orderSkuItems are needed)
//
// Action: StockOrderRedLeafEntityToOrderItemRedLeafEntity
//
// StockOrderReleasedOrderSkuItemsEvent --> StockOrderReleaseOrderSkuItemsCommand
//
// Entity: OrderItemRedLeafEntity
//
// StockOrderReleaseOrderSkuItemsCommand
// --> StockOrderReleasedOrderSkuItemsEvent
// --> OrderItemRequestsStockSkuItemsEvent

@Id("orderItemRedLeafId")
@TypeId("orderItemRedLeaf")
@RequestMapping("/order-item-red-leaf/{orderItemRedLeafId}")
public class OrderItemRedLeafEntity extends EventSourcedEntity<OrderItemRedLeafEntity.State, OrderItemRedLeafEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(OrderItemRedLeafEntity.class);
  private final String entityId;

  public OrderItemRedLeafEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/order-item-create")
  public Effect<String> orderItemCreate(@RequestBody OrderItemCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/stock-order-requests-order-sku-items")
  public Effect<String> stockOrderRequestsOrderSkuItems(@RequestBody StockOrderRequestsOrderSkuItemsCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/stock-order-release-order-sku-items")
  public Effect<String> stockOrderReleaseOrderSkuItems(@RequestBody StockOrderReleaseOrderSkuItemsCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-consumed-stock-sku-items")
  public Effect<String> orderItemConsumedStockSkuItems(@RequestBody OrderItemConsumedStockSkuItemsCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-set-back-ordered")
  public Effect<String> orderItemSetBackOrdered(@RequestBody OrderItemSetBackOrderedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isFalse(currentState().alreadyCreated(), "OrderItemRedLeaf '%s' not found".formatted(entityId))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(OrderItemCreatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemRequestsStockSkuItemsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderConsumedOrderSkuItemsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderReleasedOrderSkuItemsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemConsumedStockSkuItemsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemReleasedStockSkuItemsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemSetBackOrderedOnEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemSetBackOrderedOffEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      OrderItemRedLeafId orderItemRedLeafId,
      OrderItemRedTreeId parentId,
      int quantity,
      Instant readyToShipAt,
      Instant backOrderedAt,
      List<OrderSkuItemId> orderSkuItemsAvailable,
      List<Consumed> orderSkuItemsConsumed) {

    static State emptyState() {
      return new State(null, null, 0, null, null, List.of(), List.of());
    }

    boolean alreadyCreated() {
      return orderItemRedLeafId != null;
    }

    boolean isAvailableToBeConsumed() {
      return backOrderedAt != null;
    }

    List<Event> eventsFor(OrderItemCreateCommand command) {
      if (alreadyCreated()) {
        return List.of();
      }

      var newOrderSkuItemsAvailable = IntStream.range(0, command.quantity())
          .mapToObj(i -> OrderSkuItemId.genId(command.orderItemRedLeafId))
          .toList();

      return List.of(
          new OrderItemCreatedEvent(command.orderItemRedLeafId, command.parentId, command.quantity(), newOrderSkuItemsAvailable),
          new OrderItemRequestsStockSkuItemsEvent(command.orderItemRedLeafId, newOrderSkuItemsAvailable));
    }

    List<Event> eventsFor(StockOrderRequestsOrderSkuItemsCommand command) {
      if (!isAvailableToBeConsumed()) {
        var consumed = new Consumed(command.stockOrderRedLeafId, List.of());
        return List.of(new StockOrderConsumedOrderSkuItemsEvent(orderItemRedLeafId, parentId, command.stockOrderRedLeafId,
            readyToShipAt, backOrderedAt, orderSkuItemsAvailable, orderSkuItemsConsumed, consumed));
      }

      boolean alreadyConsumed = orderSkuItemsConsumed.stream()
          .anyMatch(consumed -> consumed.stockOrderRedLeafId().equals(command.stockOrderRedLeafId()));
      if (alreadyConsumed) {
        return List.of();
      }

      var stockSkuItemIdsQueue = new LinkedList<>(command.stockSkuItemsAvailable());
      var orderSkuItemsForStockOrder = orderSkuItemsAvailable.stream()
          .map(orderSkuItemId -> new OrderSkuItemToStockSkuItem(orderSkuItemId, stockSkuItemIdsQueue.poll()))
          .filter(orderSkuItem -> orderSkuItem.stockSkuItemId != null)
          .toList();
      var orderSkuItemIdsConsumed = orderSkuItemsForStockOrder.stream()
          .map(orderSkuItemToStockSkuItem -> orderSkuItemToStockSkuItem.orderSkuItemId())
          .toList();
      var newOrderSkuItemsAvailable = orderSkuItemsAvailable.stream()
          .filter(orderSkuItem -> !orderSkuItemIdsConsumed.contains(orderSkuItem))
          .toList();
      var newReadyToShipAt = newOrderSkuItemsAvailable.isEmpty()
          ? Instant.now()
          : null;
      var consumed = new Consumed(command.stockOrderRedLeafId(), orderSkuItemsForStockOrder);
      var newOrderSkuItemsConsumed = Stream.concat(this.orderSkuItemsConsumed.stream(), Stream.of(consumed))
          .filter(c -> c.orderSkuItemsToStockSkuItems().size() > 0)
          .toList();

      var event = new StockOrderConsumedOrderSkuItemsEvent(orderItemRedLeafId, parentId, command.stockOrderRedLeafId,
          newReadyToShipAt, null, newOrderSkuItemsAvailable, newOrderSkuItemsConsumed, consumed);
      var eventBackOrderedOff = new OrderItemSetBackOrderedOffEvent(orderItemRedLeafId, parentId, newOrderSkuItemsAvailable, newOrderSkuItemsConsumed);
      var eventRequests = new OrderItemRequestsStockSkuItemsEvent(orderItemRedLeafId, newOrderSkuItemsAvailable);

      log.debug("===== {} -> {}, available {}, requested {}, consumed {}, available {}", command.stockOrderRedLeafId, orderItemRedLeafId, orderSkuItemsAvailable.size(),
          command.stockSkuItemsAvailable().size(), consumed.orderSkuItemsToStockSkuItems.size(), newOrderSkuItemsAvailable.size()); // TODO: remove after testing

      return newOrderSkuItemsAvailable.isEmpty()
          ? List.of(event, eventBackOrderedOff)
          : List.of(event, eventRequests, eventBackOrderedOff);
    }

    List<Event> eventsFor(StockOrderReleaseOrderSkuItemsCommand command) {
      var orderSkuItemsReleased = orderSkuItemsConsumed.stream()
          .filter(consumed -> consumed.stockOrderRedLeafId().equals(command.stockOrderRedLeafId()))
          .findFirst()
          .map(consumed -> consumed.orderSkuItemsToStockSkuItems())
          .orElse(List.of())
          .stream()
          .map(orderSkuItemToStockSkuItem -> orderSkuItemToStockSkuItem.orderSkuItemId())
          .toList();

      if (orderSkuItemsReleased.isEmpty()) {
        return List.of(); // already released
      }

      var newOrderSkuItemsAvailable = Stream.concat(orderSkuItemsAvailable.stream(), orderSkuItemsReleased.stream()).toList();
      var newOrderSkuItemsConsumed = orderSkuItemsConsumed.stream()
          .filter(consumedStockOrder -> !consumedStockOrder.stockOrderRedLeafId().equals(command.stockOrderRedLeafId()))
          .toList();
      var newReadyToShipAt = newOrderSkuItemsAvailable.size() == 0
          ? Instant.now()
          : null;

      var eventReleased = new StockOrderReleasedOrderSkuItemsEvent(orderItemRedLeafId, parentId, command.stockOrderRedLeafId(),
          newReadyToShipAt, newOrderSkuItemsAvailable, newOrderSkuItemsConsumed);
      var eventNeeded = new OrderItemRequestsStockSkuItemsEvent(orderItemRedLeafId, newOrderSkuItemsAvailable);

      return List.of(eventReleased, eventNeeded);
    }

    List<Event> eventsFor(OrderItemConsumedStockSkuItemsCommand command) {
      var consumed = new Consumed(command.stockOrderRedLeafId, command.orderSkuItemsConsumed);
      boolean alreadyConsumed = orderSkuItemsConsumed.stream()
          .anyMatch(c -> c.equals(consumed));
      if (alreadyConsumed) {
        return List.of();
      }

      var areAllOrderSkuItemsToBeConsumedAvailable = command.orderSkuItemsConsumed.stream()
          .allMatch(orderSkuItemToStockSkuItem -> orderSkuItemsAvailable.contains(orderSkuItemToStockSkuItem.orderSkuItemId()));

      if (!areAllOrderSkuItemsToBeConsumedAvailable) {
        var event = new OrderItemReleasedStockSkuItemsEvent(orderItemRedLeafId, parentId, command.stockOrderRedLeafId(), command.orderSkuItemsConsumed);

        return orderSkuItemsAvailable.size() > 0
            ? List.of(event, new OrderItemRequestsStockSkuItemsEvent(orderItemRedLeafId, orderSkuItemsAvailable))
            : List.of(event);
      }

      var orderSkuItemIdsConsumed = command.orderSkuItemsConsumed.stream()
          .map(OrderSkuItemToStockSkuItem::orderSkuItemId)
          .toList();
      var newOrderSkuItemsAvailable = orderSkuItemsAvailable.stream()
          .filter(orderSkuItem -> !orderSkuItemIdsConsumed.contains(orderSkuItem))
          .toList();
      var filteredOrderSkuItemsConsumed = orderSkuItemsConsumed.stream()
          .filter(c -> !c.stockOrderRedLeafId().equals(command.stockOrderRedLeafId()))
          .toList();
      var newOrderSkuItemsConsumed = Stream.concat(filteredOrderSkuItemsConsumed.stream(), Stream.of(consumed))
          .filter(c -> c.orderSkuItemsToStockSkuItems().size() > 0)
          .toList();
      var newReadyToShipAt = newOrderSkuItemsAvailable.isEmpty()
          ? Instant.now()
          : null;
      var newBackOrderedAt = newOrderSkuItemsAvailable.isEmpty()
          ? null
          : backOrderedAt;
      var event = new OrderItemConsumedStockSkuItemsEvent(orderItemRedLeafId, parentId, command.stockOrderRedLeafId,
          newReadyToShipAt, newBackOrderedAt, newOrderSkuItemsAvailable, newOrderSkuItemsConsumed);
      var eventRequests = new OrderItemRequestsStockSkuItemsEvent(orderItemRedLeafId, newOrderSkuItemsAvailable);
      var eventBackOrderedOff = backOrderedAt != null && newBackOrderedAt == null
          ? new OrderItemSetBackOrderedOffEvent(orderItemRedLeafId, parentId, newOrderSkuItemsAvailable, newOrderSkuItemsConsumed)
          : null;

      return eventBackOrderedOff != null
          ? newOrderSkuItemsAvailable.isEmpty()
              ? List.of(event, eventBackOrderedOff)
              : List.of(event, eventRequests, eventBackOrderedOff)
          : newOrderSkuItemsAvailable.isEmpty()
              ? List.of(event)
              : List.of(event, eventRequests);
    }

    List<Event> eventsFor(OrderItemSetBackOrderedCommand command) {
      if (isAvailableToBeConsumed()) {
        return List.of();
      }

      return List.of(new OrderItemSetBackOrderedOnEvent(command.orderItemRedLeafId, parentId, Instant.now(), orderSkuItemsAvailable, orderSkuItemsConsumed));
    }

    State on(OrderItemCreatedEvent event) {
      return new State(event.orderItemRedLeafId(), event.parentId(), event.quantity(), null, null, event.orderSkuItemsAvailable, List.of());
    }

    State on(OrderItemRequestsStockSkuItemsEvent event) {
      return this;
    }

    State on(StockOrderConsumedOrderSkuItemsEvent event) {
      return new State(orderItemRedLeafId, parentId, quantity, event.readyToShipAt, event.backOrderedAt, event.orderSkuItemsAvailable, event.orderSkuItemsConsumed);
    }

    State on(StockOrderReleasedOrderSkuItemsEvent event) {
      return new State(orderItemRedLeafId, parentId, quantity, event.readyToShipAt, backOrderedAt, event.orderSkuItemsAvailable, event.orderSkuItemsConsumed);
    }

    State on(OrderItemConsumedStockSkuItemsEvent event) {
      return new State(orderItemRedLeafId, parentId, quantity, event.readyToShipAt, event.backOrderedAt, event.orderSkuItemsAvailable, event.orderSkuItemsConsumed);
    }

    State on(OrderItemReleasedStockSkuItemsEvent event) {
      return this;
    }

    State on(OrderItemSetBackOrderedOnEvent event) {
      return new State(orderItemRedLeafId, parentId, quantity, null, event.backOrderedAt(), orderSkuItemsAvailable, orderSkuItemsConsumed);
    }

    State on(OrderItemSetBackOrderedOffEvent event) {
      return new State(orderItemRedLeafId, parentId, quantity, readyToShipAt, null, event.orderSkuItemsAvailable(), event.orderSkuItemsConsumed());
    }
  }

  public record OrderItemRedLeafId(String orderId, String skuId, UUID uuId) {
    public static OrderItemRedLeafId genId(String orderId, String skuId) {
      return new OrderItemRedLeafId(orderId, skuId, UUID.randomUUID());
    }

    public String toEntityId() {
      return "%s_%s_%s".formatted(orderId, skuId, uuId);
    }
  }

  public record OrderSkuItemId(OrderItemRedLeafId orderItemRedLeafId, UUID orderSkuId) {
    public static OrderSkuItemId genId(OrderItemRedLeafId orderItemRedLeafId) {
      return new OrderSkuItemId(orderItemRedLeafId, UUID.randomUUID());
    }
  }

  public record Consumed(StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId, List<OrderSkuItemToStockSkuItem> orderSkuItemsToStockSkuItems) {
    static int quantityConsumed(List<Consumed> consumed) {
      return consumed.stream()
          .flatMap(consumedStockOrder -> consumedStockOrder.orderSkuItemsToStockSkuItems().stream())
          .mapToInt(orderSkuItemToStockSkuItem -> 1)
          .sum();
    }
  }

  public record OrderSkuItemToStockSkuItem(OrderSkuItemId orderSkuItemId, StockOrderRedLeafEntity.StockSkuItemId stockSkuItemId) {}

  public interface Event {}

  public record OrderItemCreateCommand(OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeId parentId, int quantity) {}

  public record OrderItemCreatedEvent(OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeId parentId,
      int quantity,
      List<OrderSkuItemId> orderSkuItemsAvailable) implements Event {}

  public record OrderItemRequestsStockSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, List<OrderSkuItemId> orderSkuItemIds) implements Event {}

  public record StockOrderRequestsOrderSkuItemsCommand(OrderItemRedLeafId orderItemRedLeafId,
      StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId,
      List<StockOrderRedLeafEntity.StockSkuItemId> stockSkuItemsAvailable) {}

  public record StockOrderConsumedOrderSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeId parentId,
      StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId,
      Instant readyToShipAt, Instant backOrderedAt,
      List<OrderSkuItemId> orderSkuItemsAvailable, List<Consumed> orderSkuItemsConsumed, Consumed consumed) implements Event {}

  public record StockOrderReleaseOrderSkuItemsCommand(OrderItemRedLeafId orderItemRedLeafId,
      StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId) {}

  public record StockOrderReleasedOrderSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeId parentId,
      StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId,
      Instant readyToShipAt,
      List<OrderSkuItemId> orderSkuItemsAvailable, List<Consumed> orderSkuItemsConsumed) implements Event {}

  public record OrderItemConsumedStockSkuItemsCommand(OrderItemRedLeafId orderItemRedLeafId,
      StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId,
      List<OrderSkuItemToStockSkuItem> orderSkuItemsConsumed) {}

  public record OrderItemConsumedStockSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeId parentId,
      StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId,
      Instant readyToShipAt, Instant backOrderedAt,
      List<OrderSkuItemId> orderSkuItemsAvailable, List<Consumed> orderSkuItemsConsumed) implements Event {}

  public record OrderItemReleasedStockSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeId parentId,
      StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafId,
      List<OrderSkuItemToStockSkuItem> orderSkuItemsReleased) implements Event {}

  public record OrderItemSetBackOrderedCommand(OrderItemRedLeafId orderItemRedLeafId) {}

  public record OrderItemSetBackOrderedOnEvent(OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeId parentId,
      Instant backOrderedAt, List<OrderSkuItemId> orderSkuItemsAvailable, List<Consumed> orderSkuItemsConsumed) implements Event {}

  public record OrderItemSetBackOrderedOffEvent(OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeId parentId,
      List<OrderSkuItemId> orderSkuItemsAvailable, List<Consumed> orderSkuItemsConsumed) implements Event {}
}