package io.example.stock;

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
import io.example.shipping.OrderItemRedLeafEntity.OrderItemRedLeafId;
import io.example.shipping.OrderItemRedLeafEntity.OrderSkuItemId;
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

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
  public Effect<String> createStockOrderRedLeaf(@RequestBody StockOrderRedLeafCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-requests-stock-sku-items")
  public Effect<String> orderItemRequestsStockSkuItems(@RequestBody OrderItemRequestsStockSkuItemsCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-releases-stock-sku-items")
  public Effect<String> orderItemReleaseOrderSkuItems(@RequestBody OrderItemReleaseOrderSkuItemsCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/stock-order-consumed-order-sku-items")
  public Effect<String> stockOrderConsumedOrderSkuItems(@RequestBody StockOrderConsumedOrderSkuItemsCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/stock-order-set-available-to-be-consumed")
  public Effect<String> stockOrderSetAvailableToBeConsumed(@RequestBody StockOrderSetAvailableToBeConsumedCommand command) {
    log.info("EntityId: {}\n_State: {}", entityId, currentState());
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isFalse(currentState().alreadyCreated(), "StockOrderRedLeaf '%s' not found".formatted(entityId))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(StockOrderRedLeafCreatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderRequestsOrderSkuItemsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemConsumedStockSkuItemsEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemReleasedOrderSkuItemsEvent event) {
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
  public State on(StockOrderSetAvailableToBeConsumedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      StockOrderRedLeafId stockOrderRedLeafId,
      int quantity,
      boolean availableToBeConsumed,
      List<StockSkuItemId> stockSkuItemIds,
      List<Consumed> consumedOrderItems) {

    static State emptyState() {
      return new State(null, 0, false, List.of(), List.of());
    }

    boolean alreadyCreated() {
      return stockOrderRedLeafId != null;
    }

    List<Event> eventsFor(StockOrderRedLeafCreateCommand command) {
      if (alreadyCreated()) {
        return List.of();
      }

      var stockSkuItems = IntStream.range(0, command.quantity())
          .mapToObj(i -> StockSkuItemId.of(command.stockOrderRedLeafId))
          .toList();

      return List.of(
          new StockOrderRedLeafCreatedEvent(command.stockOrderRedLeafId, command.quantity(), stockSkuItems),
          new StockOrderRequestsOrderSkuItemsEvent(command.stockOrderRedLeafId, stockSkuItems));
    }

    List<Event> eventsFor(OrderItemRequestsStockSkuItemsCommand command) {
      if (!availableToBeConsumed) {
        return List.of(new OrderItemConsumedStockSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId, List.of()));
      }
      boolean alreadyConsumed = consumedOrderItems.stream()
          .anyMatch(consumed -> consumed.orderItemRedLeafId().equals(command.orderItemRedLeafId()));
      if (alreadyConsumed) {
        return List.of();
      }

      var orderSkuItemIdsQueue = new LinkedList<>(command.orderSkuItemIds);
      var stockSkuItemsForOrderItem = stockSkuItemIds.stream()
          .map(stockSkuItemId -> new StockSkuItemToOrderSkuItem(stockSkuItemId, orderSkuItemIdsQueue.poll()))
          .filter(stockSkuItemToOrderSkuItem -> stockSkuItemToOrderSkuItem.orderSkuItemId() != null)
          .toList();

      return List.of(new OrderItemConsumedStockSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId, stockSkuItemsForOrderItem));
    }

    List<Event> eventsFor(OrderItemReleaseOrderSkuItemsCommand command) {
      boolean alreadyReleased = !consumedOrderItems.stream()
          .anyMatch(consumed -> consumed.orderItemRedLeafId().equals(command.orderItemRedLeafId));

      if (alreadyReleased) {
        return List.of();
      }

      var eventReleased = new OrderItemReleasedOrderSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId);
      var newState = on(eventReleased);
      var eventNeeded = new StockOrderRequestsOrderSkuItemsEvent(stockOrderRedLeafId, newState.stockSkuItemIds);

      return List.of(eventReleased, eventNeeded);
    }

    List<Event> eventsFor(StockOrderConsumedOrderSkuItemsCommand command) {
      boolean alreadyConsumed = consumedOrderItems.stream()
          .anyMatch(consumed -> consumed.orderItemRedLeafId().equals(command.orderItemRedLeafId));
      if (alreadyConsumed) {
        return List.of();
      }

      var allStockSkuItemsConsumedAreAvailable = command.stockSkuItemsToOrderSkuItems.stream()
          .allMatch(stockSkuItemToOrderSkuItem -> stockSkuItemIds.contains(stockSkuItemToOrderSkuItem.stockSkuItemId()));

      if (!allStockSkuItemsConsumedAreAvailable) {
        var event = new StockOrderReleasedOrderSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId());
        return stockSkuItemIds.size() > 0
            ? List.of(event, new StockOrderRequestsOrderSkuItemsEvent(stockOrderRedLeafId, stockSkuItemIds))
            : List.of(event);
      }

      var orderSkuItemIds = command.stockSkuItemsToOrderSkuItems.stream()
          .map(StockSkuItemToOrderSkuItem::orderSkuItemId)
          .toList();
      var orderSkuItemIdsQueue = new LinkedList<>(orderSkuItemIds);
      var stockSkuItemsForStock = stockSkuItemIds.stream()
          .map(stockSkuItemId -> new StockSkuItemToOrderSkuItem(stockSkuItemId, orderSkuItemIdsQueue.poll()))
          .filter(stockSkuItemToOrderSkuItem -> stockSkuItemToOrderSkuItem.orderSkuItemId() != null)
          .toList();

      var event = new StockOrderConsumedOrderSkuItemsEvent(stockOrderRedLeafId, command.orderItemRedLeafId, stockSkuItemsForStock);
      var stockSkuItemsNeeded = stockSkuItemIds.stream()
          .skip(stockSkuItemsForStock.size())
          .toList();
      return stockSkuItemsNeeded.isEmpty()
          ? List.of(event)
          : List.of(event, new StockOrderRequestsOrderSkuItemsEvent(stockOrderRedLeafId, stockSkuItemsNeeded));
    }

    List<Event> eventsFor(StockOrderSetAvailableToBeConsumedCommand command) {
      if (availableToBeConsumed) {
        return List.of();
      }

      return List.of(new StockOrderSetAvailableToBeConsumedEvent(stockOrderRedLeafId));
    }

    State on(StockOrderRedLeafCreatedEvent event) {
      return new State(event.stockOrderRedLeafId(), event.quantity(), false, event.stockSkuItemIds, List.of());
    }

    State on(StockOrderRequestsOrderSkuItemsEvent event) {
      return this;
    }

    State on(OrderItemConsumedStockSkuItemsEvent event) {
      if (event.stockSkuItemToOrderSkuItems().isEmpty()) {
        return this;
      }
      var stockSkuItemIds = event.stockSkuItemToOrderSkuItems().stream()
          .map(stockSkuItemToOrderSkuItem -> stockSkuItemToOrderSkuItem.stockSkuItemId())
          .toList();
      var newStockSkuItemIds = this.stockSkuItemIds.stream()
          .filter(stockSkuItemId -> !stockSkuItemIds.contains(stockSkuItemId))
          .toList();
      var consumed = new Consumed(event.orderItemRedLeafId(), event.stockSkuItemToOrderSkuItems());
      var newConsumedOrderItems = Stream.concat(this.consumedOrderItems.stream(), Stream.of(consumed)).toList();

      return new State(stockOrderRedLeafId, quantity, availableToBeConsumed, newStockSkuItemIds, newConsumedOrderItems);
    }

    State on(OrderItemReleasedOrderSkuItemsEvent event) {
      var consumedToBeReleased = consumedOrderItems.stream()
          .filter(consumed -> consumed.orderItemRedLeafId().equals(event.orderItemRedLeafId))
          .findFirst();

      if (consumedToBeReleased.isEmpty()) {
        return this;
      }

      var newStockSkuItem = consumedToBeReleased.get().stockSkuItemsToOrderSkuItems().stream()
          .map(StockSkuItemToOrderSkuItem::stockSkuItemId)
          .toList();
      var newConsumedOrderItems = consumedOrderItems.stream()
          .filter(consumed -> !consumed.orderItemRedLeafId().equals(event.orderItemRedLeafId))
          .toList();

      return new State(stockOrderRedLeafId, quantity, availableToBeConsumed, newStockSkuItem, newConsumedOrderItems);
    }

    State on(StockOrderConsumedOrderSkuItemsEvent event) {
      var consumedStockSkuItemsIds = event.stockSkuItemsToOrderSkuItems().stream()
          .map(StockSkuItemToOrderSkuItem::stockSkuItemId)
          .toList();
      var newStockSkuItemIds = stockSkuItemIds.stream()
          .filter(stockSkuItemId -> !consumedStockSkuItemsIds.contains(stockSkuItemId))
          .toList();
      var consumed = new Consumed(event.orderItemRedLeafId(), event.stockSkuItemsToOrderSkuItems());
      var newConsumedOrderItems = Stream.concat(consumedOrderItems.stream(), Stream.of(consumed)).toList();

      return new State(stockOrderRedLeafId, quantity, availableToBeConsumed, newStockSkuItemIds, newConsumedOrderItems);
    }

    State on(StockOrderReleasedOrderSkuItemsEvent event) {
      return this;
    }

    State on(StockOrderSetAvailableToBeConsumedEvent event) {
      return new State(stockOrderRedLeafId, quantity, true, stockSkuItemIds, consumedOrderItems);
    }
  }

  public record StockOrderRedLeafId(String orderId, String skuId) {
    public static StockOrderRedLeafId of(String orderId, String skuId) {
      return new StockOrderRedLeafId(orderId, skuId);
    }

    public String toEntityId() {
      return "%s_%s".formatted(orderId, skuId);
    }
  }

  public record StockSkuItemId(StockOrderRedLeafId stockOrderRedLeafId, UUID stockSkuId) {
    public static StockSkuItemId of(StockOrderRedLeafId stockOrderRedLeafId) {
      return new StockSkuItemId(stockOrderRedLeafId, UUID.randomUUID());
    }
  }

  public record Consumed(OrderItemRedLeafId orderItemRedLeafId, List<StockSkuItemToOrderSkuItem> stockSkuItemsToOrderSkuItems) {}

  public record StockSkuItemToOrderSkuItem(StockSkuItemId stockSkuItemId, OrderSkuItemId orderSkuItemId) {}

  public interface Event {}

  public record StockOrderRedLeafCreateCommand(StockOrderRedLeafId stockOrderRedLeafId, int quantity) {}

  public record StockOrderRedLeafCreatedEvent(StockOrderRedLeafId stockOrderRedLeafId, int quantity, List<StockSkuItemId> stockSkuItemIds) implements Event {}

  public record StockOrderRequestsOrderSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, List<StockSkuItemId> stockSkuItemIds) implements Event {}

  public record OrderItemRequestsStockSkuItemsCommand(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId, List<OrderSkuItemId> orderSkuItemIds) {}

  public record OrderItemConsumedStockSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId, List<StockSkuItemToOrderSkuItem> stockSkuItemToOrderSkuItems) implements Event {}

  public record OrderItemReleaseOrderSkuItemsCommand(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId) {}

  public record OrderItemReleasedOrderSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId) implements Event {}

  public record StockOrderConsumedOrderSkuItemsCommand(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId, List<StockSkuItemToOrderSkuItem> stockSkuItemsToOrderSkuItems) {}

  public record StockOrderConsumedOrderSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId, List<StockSkuItemToOrderSkuItem> stockSkuItemsToOrderSkuItems) implements Event {}

  public record StockOrderReleasedOrderSkuItemsEvent(StockOrderRedLeafId stockOrderRedLeafId, OrderItemRedLeafId orderItemRedLeafId) implements Event {}

  public record StockOrderSetAvailableToBeConsumedCommand(StockOrderRedLeafId stockOrderRedLeafId) {}

  public record StockOrderSetAvailableToBeConsumedEvent(StockOrderRedLeafId stockOrderRedLeafId) implements Event {}
}