package io.example.stock;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.shipping.OrderItemRedLeafEntity.OrderItemRedLeafId;
import io.example.shipping.OrderItemRedLeafEntity.OrderSkuItemId;
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

@Id("stockOrderRedLeafId")
@TypeId("stockOrderRedLeaf")
@RequestMapping("/stock-order-red-leaf/{stockOrderRedLeafId}")
public class StockOrderRedLeafEntity extends EventSourcedEntity<StockOrderRedLeafEntity.State, StockOrderRedLeafEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(StockOrderRedLeafEntity.class);
  private final String entityId;

  public StockOrderRedLeafEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/stock-order-create")
  public Effect<String> stockOrderCreate(@RequestBody StockOrderCreateCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-requests-stock-sku-items")
  public Effect<String> orderItemRequestsStockSkuItems(@RequestBody OrderItemRequestsStockSkuItemsCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-releases-stock-sku-items")
  public Effect<String> orderItemReleaseOrderSkuItems(@RequestBody OrderItemReleaseStockSkuItemsCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/stock-order-consumed-order-sku-items")
  public Effect<String> stockOrderConsumedOrderSkuItems(@RequestBody StockOrderConsumedOrderSkuItemsCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/stock-order-set-available-to-be-consumed")
  public Effect<String> stockOrderSetAvailableToBeConsumed(@RequestBody StockOrderSetAvailableToBeConsumedOnCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_Get", entityId, currentState());
    return Validator
        .isFalse(currentState().alreadyCreated(), "StockOrderRedLeaf '%s' not found".formatted(entityId))
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @EventHandler
  public State on(StockOrderCreatedEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderRequestsOrderSkuItemsEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemConsumedStockSkuItemsEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemReleasedStockSkuItemsEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderConsumedOrderSkuItemsEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderReleasedOrderSkuItemsEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderSetAvailableToBeConsumedEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderUpdatedEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      StockOrderRedLeafId stockOrderRedLeafId,
      int quantity,
      boolean availableToBeConsumed,
      List<StockSkuItemId> stockSkuItemsAvailable,
      List<Consumed> stockSkuItemsConsumed) {

    static State emptyState() {
      return new State(null, 0, false, List.of(), List.of());
    }

    boolean alreadyCreated() {
      return stockOrderRedLeafId != null;
    }

    List<Event> eventsFor(StockOrderCreateCommand command) {
      if (alreadyCreated()) {
        return List.of();
      }

      var newStockSkuItemsAvailable = command.stockSkuItemIds();

      return List.of(
          new StockOrderCreatedEvent(command.stockOrderRedLeafId, newStockSkuItemsAvailable),
          new StockOrderRequestsOrderSkuItemsEvent(command.stockOrderRedLeafId, newStockSkuItemsAvailable));
    }

    List<Event> eventsFor(OrderItemRequestsStockSkuItemsCommand command) {
      var alreadyConsumed = stockSkuItemsConsumed.stream()
          .filter(consumed -> consumed.orderItemRedLeafId().equals(command.orderItemRedLeafId()))
          .findFirst();
      if (alreadyConsumed.isPresent()) { // Idempotent response - already consumed
        var consumed = alreadyConsumed.get();
        return List.of(new OrderItemConsumedStockSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId,
            availableToBeConsumed, stockSkuItemsAvailable, stockSkuItemsConsumed, consumed));
      }

      if (!availableToBeConsumed) {
        var consumed = new Consumed(command.orderItemRedLeafId, List.of());
        return List.of(new OrderItemConsumedStockSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId,
            availableToBeConsumed, stockSkuItemsAvailable, stockSkuItemsConsumed, consumed));
      }

      var orderSkuItemIdsQueue = new LinkedList<>(command.orderSkuItemsAvailable);
      var stockSkuItemsForOrderItem = stockSkuItemsAvailable.stream()
          .map(stockSkuItemId -> new StockSkuItemToOrderSkuItem(stockSkuItemId, orderSkuItemIdsQueue.poll()))
          .filter(stockSkuItemToOrderSkuItem -> stockSkuItemToOrderSkuItem.orderSkuItemId() != null)
          .toList();
      var stockSkuItemIds = stockSkuItemsForOrderItem.stream()
          .map(stockSkuItemToOrderSkuItem -> stockSkuItemToOrderSkuItem.stockSkuItemId())
          .toList();
      var newStockSkuItemIds = this.stockSkuItemsAvailable.stream()
          .filter(stockSkuItemId -> !stockSkuItemIds.contains(stockSkuItemId))
          .toList();
      var newAvailableToBeConsumed = newStockSkuItemIds.isEmpty()
          ? false
          : availableToBeConsumed;
      var consumed = new Consumed(command.orderItemRedLeafId(), stockSkuItemsForOrderItem);
      var newStockSkuItemIdsConsumed = Stream.concat(this.stockSkuItemsConsumed.stream(), Stream.of(consumed))
          .filter(c -> c.stockSkuItemsToOrderSkuItems().size() > 0)
          .toList();

      return List.of(new OrderItemConsumedStockSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId,
          newAvailableToBeConsumed, newStockSkuItemIds, newStockSkuItemIdsConsumed, consumed));
    }

    List<Event> eventsFor(OrderItemReleaseStockSkuItemsCommand command) {
      var stockSkuItemsReleased = stockSkuItemsConsumed.stream()
          .filter(consumed -> consumed.orderItemRedLeafId().equals(command.orderItemRedLeafId))
          .findFirst()
          .map(consumed -> consumed.stockSkuItemsToOrderSkuItems())
          .orElse(List.of())
          .stream()
          .map(stockSkuItemToOrderSkuItem -> stockSkuItemToOrderSkuItem.stockSkuItemId())
          .toList();

      if (stockSkuItemsReleased.isEmpty()) {
        return List.of(); // already released
      }

      var newStockSkuItemAvailable = Stream.concat(stockSkuItemsAvailable.stream(), stockSkuItemsReleased.stream()).toList();
      var newConsumedOrderItems = stockSkuItemsConsumed.stream()
          .filter(consumed -> !consumed.orderItemRedLeafId().equals(command.orderItemRedLeafId))
          .toList();

      var eventReleased = new OrderItemReleasedStockSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId,
          newStockSkuItemAvailable, newConsumedOrderItems);
      var eventRequests = new StockOrderRequestsOrderSkuItemsEvent(stockOrderRedLeafId, newStockSkuItemAvailable);

      return List.of(eventReleased, eventRequests);
    }

    List<Event> eventsFor(StockOrderConsumedOrderSkuItemsCommand command) {
      if (command.stockSkuItemsConsumed.isEmpty()) {
        return stockSkuItemsAvailable.isEmpty()
            ? List.of()
            : List.of(new StockOrderRequestsOrderSkuItemsEvent(stockOrderRedLeafId, stockSkuItemsAvailable));
      }

      var consumed = new Consumed(command.orderItemRedLeafId(), command.stockSkuItemsConsumed());
      var alreadyConsumed = stockSkuItemsConsumed.stream()
          .anyMatch(c -> c.equals(consumed));
      if (alreadyConsumed) {
        return List.of();
      }

      var areAllStockSkuItemsToBeConsumedAvailable = command.stockSkuItemsConsumed.stream()
          .allMatch(stockSkuItemToOrderSkuItem -> stockSkuItemsAvailable.contains(stockSkuItemToOrderSkuItem.stockSkuItemId()));

      if (!areAllStockSkuItemsToBeConsumedAvailable) {
        var event = new StockOrderReleasedOrderSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId(), command.stockSkuItemsConsumed);

        return stockSkuItemsAvailable.size() > 0
            ? List.of(event, new StockOrderRequestsOrderSkuItemsEvent(stockOrderRedLeafId, stockSkuItemsAvailable))
            : List.of(event);
      }

      var stockSkuItemIdsConsumed = command.stockSkuItemsConsumed.stream()
          .map(StockSkuItemToOrderSkuItem::stockSkuItemId)
          .toList();
      var newStockSkuItemsAvailable = stockSkuItemsAvailable.stream()
          .filter(stockSkuItemId -> !stockSkuItemIdsConsumed.contains(stockSkuItemId))
          .toList();
      var filteredOrderSkuItemsConsumed = stockSkuItemsConsumed.stream()
          .filter(c -> !c.orderItemRedLeafId().equals(command.orderItemRedLeafId()))
          .toList();
      var newOrderItemsConsumed = Stream.concat(filteredOrderSkuItemsConsumed.stream(), Stream.of(consumed))
          .filter(c -> c.stockSkuItemsToOrderSkuItems().size() > 0)
          .toList();
      var newAvailableToBeConsumed = newStockSkuItemsAvailable.isEmpty()
          ? false
          : availableToBeConsumed;
      var event = new StockOrderConsumedOrderSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId, newAvailableToBeConsumed,
          newStockSkuItemsAvailable, newOrderItemsConsumed);

      log.debug("===== {} -> {}, available {}, consumed {}, available {}, consumed {}", stockOrderRedLeafId, command.orderItemRedLeafId,
          stockSkuItemsAvailable.size(), command.stockSkuItemsConsumed.size(),
          newStockSkuItemsAvailable.size(), consumed.stockSkuItemsToOrderSkuItems().size()); // TODO: remove after testing

      return newStockSkuItemsAvailable.isEmpty()
          ? List.of(event)
          : List.of(event, new StockOrderRequestsOrderSkuItemsEvent(stockOrderRedLeafId, newStockSkuItemsAvailable));
    }

    List<Event> eventsFor(StockOrderSetAvailableToBeConsumedOnCommand command) {
      if (availableToBeConsumed) {
        return List.of();
      }

      var event = new StockOrderSetAvailableToBeConsumedEvent(stockOrderRedLeafId, true);
      var updatedEvent = new StockOrderUpdatedEvent(stockOrderRedLeafId, quantity, quantity - stockSkuItemsAvailable.size());

      return List.of(event, updatedEvent);
    }

    State on(StockOrderCreatedEvent event) {
      return new State(event.stockOrderRedLeafId(), event.stockSkuItemsAvailable().size(), false, event.stockSkuItemsAvailable, List.of());
    }

    State on(StockOrderRequestsOrderSkuItemsEvent event) {
      return this;
    }

    State on(OrderItemConsumedStockSkuItemsEvent event) {
      return new State(stockOrderRedLeafId, quantity, event.availableToBeConsumed, event.stockSkuItemsAvailable, event.stockSkuItemsConsumed);
    }

    State on(OrderItemReleasedStockSkuItemsEvent event) {
      return new State(stockOrderRedLeafId, quantity, availableToBeConsumed, event.stockSkuItemsAvailable, event.stockSkuItemsConsumed);
    }

    State on(StockOrderConsumedOrderSkuItemsEvent event) {
      return new State(stockOrderRedLeafId, quantity, event.availableToBeConsumed, event.stockSkuItemsAvailable, event.stockSkuItemsConsumed);
    }

    State on(StockOrderReleasedOrderSkuItemsEvent event) {
      return this;
    }

    State on(StockOrderSetAvailableToBeConsumedEvent event) {
      return new State(stockOrderRedLeafId, quantity, event.availableToBeConsumed, stockSkuItemsAvailable, stockSkuItemsConsumed);
    }

    State on(StockOrderUpdatedEvent event) {
      return this;
    }
  }

  public record StockOrderRedLeafId(String stockOrderId, String skuId, int branchLevel, int branchNumber, UUID uuid) {
    public static StockOrderRedLeafId genId(String stockOrderId, String skuId, int quantityLeavesPerTree, int quantityLeavesPerBranch) {
      var uuid = UUID.randomUUID();
      var branchLevel = (int) Math.ceil(Math.log(quantityLeavesPerTree) / Math.log(quantityLeavesPerBranch));
      var branchNumber = Math.abs(uuid.hashCode() % quantityLeavesPerBranch);

      return new StockOrderRedLeafId(stockOrderId, skuId, branchLevel, branchNumber, uuid);
    }

    public String toEntityId() {
      return "%s_%s_%d_%d_%s".formatted(stockOrderId, skuId, branchLevel, branchNumber, uuid);
    }

    public StockOrderRedTreeEntity.StockOrderRedTreeId parentId() {
      return StockOrderRedTreeEntity.StockOrderRedTreeId.of(this).levelDown();
    }
  }

  public record StockSkuItemId(String StockOrderId, String skuId, UUID stockSkuId) {
    public static StockSkuItemId of(StockOrderRedLeafId stockOrderRedLeafId) {
      return new StockSkuItemId(stockOrderRedLeafId.stockOrderId(), stockOrderRedLeafId.skuId(), UUID.randomUUID());
    }

    public static StockSkuItemId of(String stockOrderId, String skuId, UUID uuid) {
      return new StockSkuItemId(stockOrderId, skuId, uuid);
    }
  }

  public record Consumed(OrderItemRedLeafId orderItemRedLeafId, List<StockSkuItemToOrderSkuItem> stockSkuItemsToOrderSkuItems) {
    static int quantityConsumed(List<Consumed> consumed) {
      return consumed.stream()
          .flatMap(consumed1 -> consumed1.stockSkuItemsToOrderSkuItems().stream())
          .mapToInt(stockSkuItemToOrderSkuItem -> 1)
          .sum();
    }
  }

  public record StockSkuItemToOrderSkuItem(StockSkuItemId stockSkuItemId, OrderSkuItemId orderSkuItemId) {}

  public interface Event {}

  public record StockOrderCreateCommand(StockOrderRedLeafId stockOrderRedLeafId, List<StockSkuItemId> stockSkuItemIds) {}

  public record StockOrderCreatedEvent(StockOrderRedLeafId stockOrderRedLeafId,
      List<StockSkuItemId> stockSkuItemsAvailable) implements Event {}

  public record StockOrderRequestsOrderSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, List<StockSkuItemId> stockSkuItemIds) implements Event {}

  public record OrderItemRequestsStockSkuItemsCommand(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId,
      List<OrderSkuItemId> orderSkuItemsAvailable) {}

  public record OrderItemConsumedStockSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId,
      boolean availableToBeConsumed,
      List<StockSkuItemId> stockSkuItemsAvailable, List<Consumed> stockSkuItemsConsumed, Consumed consumed) implements Event {}

  public record OrderItemReleaseStockSkuItemsCommand(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId) {}

  public record OrderItemReleasedStockSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId,
      List<StockSkuItemId> stockSkuItemsAvailable, List<Consumed> stockSkuItemsConsumed) implements Event {}

  public record StockOrderConsumedOrderSkuItemsCommand(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId,
      List<StockSkuItemToOrderSkuItem> stockSkuItemsConsumed) {}

  public record StockOrderConsumedOrderSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId,
      boolean availableToBeConsumed,
      List<StockSkuItemId> stockSkuItemsAvailable, List<Consumed> stockSkuItemsConsumed) implements Event {}

  public record StockOrderReleasedOrderSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId,
      List<StockSkuItemToOrderSkuItem> stockSkuItemsReleased) implements Event {}

  public record StockOrderSetAvailableToBeConsumedOnCommand(StockOrderRedLeafId stockOrderRedLeafId) {}

  public record StockOrderSetAvailableToBeConsumedEvent(StockOrderRedLeafId stockOrderRedLeafId, boolean availableToBeConsumed) implements Event {}

  public record StockOrderUpdatedEvent(StockOrderRedLeafId stockOrderRedLeafId, int quantity, int quantityOrdered) implements Event {}
}