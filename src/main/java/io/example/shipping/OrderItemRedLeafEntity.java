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
import io.example.stock.StockOrderRedLeafEntity.StockOrderRedLeafId;
import io.example.stock.StockOrderRedLeafEntity.StockSkuItemId;
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

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

  @PutMapping("/create-order-item-red-leaf")
  public Effect<String> createOrderItemRedLeaf(@RequestBody OrderItemRedLeafCreateCommand command) {
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
  public State on(OrderItemRedLeafCreatedEvent event) {
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
  public State on(OrderItemSetBackOrderedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      OrderItemRedLeafId orderItemRedLeafId,
      int quantity,
      boolean availableToBeConsumed,
      Instant readyToShipAt,
      Instant backOrderedAt,
      List<OrderSkuItemId> orderSkuItemIds,
      List<Consumed> consumedStockOrders) {

    static State emptyState() {
      return new State(null, 0, false, null, null, List.of(), List.of());
    }

    boolean alreadyCreated() {
      return orderItemRedLeafId != null;
    }

    List<Event> eventsFor(OrderItemRedLeafCreateCommand command) {
      if (alreadyCreated()) {
        return List.of();
      }

      var orderSkuItems = IntStream.range(0, command.quantity())
          .mapToObj(i -> OrderSkuItemId.of(command.orderItemRedLeafId))
          .toList();

      return List.of(
          new OrderItemRedLeafCreatedEvent(command.orderItemRedLeafId, command.quantity(), orderSkuItems),
          new OrderItemRequestsStockSkuItemsEvent(command.orderItemRedLeafId, orderSkuItems));
    }

    List<Event> eventsFor(StockOrderRequestsOrderSkuItemsCommand command) {
      if (!availableToBeConsumed) {
        return List.of(new StockOrderConsumedOrderSkuItemsEvent(orderItemRedLeafId, command.stockOrderRedLeafId, List.of()));
      }
      boolean alreadyConsumed = consumedStockOrders.stream()
          .anyMatch(consumed -> consumed.stockOrderRedLeafId().equals(command.stockOrderRedLeafId()));
      if (!availableToBeConsumed || alreadyConsumed) {
        return List.of();
      }

      var stockSkuItemIdsQueue = new LinkedList<>(command.stockSkuItemIds());
      var orderSkuItemsForStockOrder = orderSkuItemIds.stream()
          .map(orderSkuItemId -> new OrderSkuItemToStockSkuItem(orderSkuItemId, stockSkuItemIdsQueue.poll()))
          .filter(orderSkuItem -> orderSkuItem.stockSkuItemId != null)
          .toList();

      return List.of(new StockOrderConsumedOrderSkuItemsEvent(orderItemRedLeafId, command.stockOrderRedLeafId, orderSkuItemsForStockOrder));
    }

    List<Event> eventsFor(StockOrderReleaseOrderSkuItemsCommand command) {
      boolean alreadyReleased = !consumedStockOrders.stream()
          .anyMatch(consumed -> consumed.stockOrderRedLeafId().equals(command.stockOrderRedLeafId()));

      if (alreadyReleased) {
        return List.of();
      }

      var eventReleased = new StockOrderReleasedOrderSkuItemsEvent(orderItemRedLeafId, command.stockOrderRedLeafId());
      var newState = on(eventReleased);
      var eventNeeded = new OrderItemRequestsStockSkuItemsEvent(orderItemRedLeafId, newState.orderSkuItemIds);

      return List.of(eventReleased, eventNeeded);
    }

    List<Event> eventsFor(OrderItemConsumedStockSkuItemsCommand command) {
      boolean alreadyConsumed = consumedStockOrders.stream()
          .anyMatch(consumed -> consumed.stockOrderRedLeafId().equals(command.stockOrderRedLeafId()));
      if (alreadyConsumed) {
        return List.of();
      }

      var allOrderSkuItemsConsumedAreAvailable = command.orderSkuItemsToStockSckItems.stream()
          .allMatch(orderSkuItemToStockSkuItem -> orderSkuItemIds.contains(orderSkuItemToStockSkuItem.orderSkuItemId()));

      if (!allOrderSkuItemsConsumedAreAvailable) {
        var event = new OrderItemReleasedStockSkuItemsEvent(orderItemRedLeafId, command.stockOrderRedLeafId());
        return orderSkuItemIds.size() > 0
            ? List.of(event, new OrderItemRequestsStockSkuItemsEvent(orderItemRedLeafId, orderSkuItemIds))
            : List.of(event);
      }

      var stockSkuItemIds = command.orderSkuItemsToStockSckItems.stream()
          .map(orderSkuItemToStockSkuItem -> orderSkuItemToStockSkuItem.stockSkuItemId())
          .toList();
      var stockSkuItemIdsQueue = new LinkedList<>(stockSkuItemIds);
      var orderSkuItemsForStock = orderSkuItemIds.stream()
          .map(orderSkuItem -> new OrderSkuItemToStockSkuItem(orderSkuItem, stockSkuItemIdsQueue.poll()))
          .filter(orderSkuItem -> orderSkuItem.stockSkuItemId != null)
          .toList();

      var event = new OrderItemConsumedStockSkuItemsEvent(orderItemRedLeafId, command.stockOrderRedLeafId, orderSkuItemsForStock);
      var orderSkuItemIdsNeeded = orderSkuItemIds.stream()
          .skip(orderSkuItemsForStock.size())
          .toList();
      return orderSkuItemIdsNeeded.isEmpty()
          ? List.of(event)
          : List.of(event, new OrderItemRequestsStockSkuItemsEvent(orderItemRedLeafId, orderSkuItemIdsNeeded));
    }

    List<Event> eventsFor(OrderItemSetBackOrderedCommand command) {
      if (availableToBeConsumed) {
        return List.of();
      }

      return List.of(new OrderItemSetBackOrderedEvent(command.orderItemRedLeafId));
    }

    State on(OrderItemRedLeafCreatedEvent event) {
      return new State(event.orderItemRedLeafId(), event.quantity(), false, null, null, event.orderSkuItemIds, List.of());
    }

    State on(OrderItemRequestsStockSkuItemsEvent event) {
      return this;
    }

    State on(StockOrderConsumedOrderSkuItemsEvent event) {
      if (event.orderSkuItemToStockSkuItems().isEmpty()) {
        return this;
      }
      var orderSkuItemIds = event.orderSkuItemToStockSkuItems().stream()
          .map(orderSkuItemToStockSkuItem -> orderSkuItemToStockSkuItem.orderSkuItemId())
          .toList();
      var newOrderSkuItems = this.orderSkuItemIds.stream()
          .filter(orderSkuItem -> !orderSkuItemIds.contains(orderSkuItem))
          .toList();
      var consumed = new Consumed(event.stockOrderRedLeafId(), event.orderSkuItemToStockSkuItems());
      var newConsumedStockOrders = Stream.concat(this.consumedStockOrders.stream(), Stream.of(consumed)).toList();

      return new State(orderItemRedLeafId, quantity, availableToBeConsumed, readyToShipAt, backOrderedAt, newOrderSkuItems, newConsumedStockOrders);
    }

    State on(StockOrderReleasedOrderSkuItemsEvent event) {
      var consumedToBeReleased = consumedStockOrders.stream()
          .filter(consumed -> consumed.stockOrderRedLeafId().equals(event.stockOrderRedLeafId()))
          .findFirst();

      if (consumedToBeReleased.isEmpty()) {
        return this;
      }

      var newOrderSkuItems = Stream.concat(this.orderSkuItemIds.stream(), consumedToBeReleased.get().orderSkuItemsToStockSkuItems().stream()
          .map(orderSkuItemToStockSkuItem -> orderSkuItemToStockSkuItem.orderSkuItemId()))
          .toList();
      var newConsumedStockOrders = consumedStockOrders.stream()
          .filter(consumedStockOrder -> !consumedStockOrder.stockOrderRedLeafId().equals(event.stockOrderRedLeafId()))
          .toList();

      return new State(orderItemRedLeafId, quantity, availableToBeConsumed, readyToShipAt, backOrderedAt, newOrderSkuItems, newConsumedStockOrders);
    }

    State on(OrderItemConsumedStockSkuItemsEvent event) {
      var consumedOrderSkuItemIds = event.orderSkuItemsToStockSckItems.stream()
          .map(orderSkuItemToStockSkuItem -> orderSkuItemToStockSkuItem.orderSkuItemId())
          .toList();
      var newOrderSkuItems = this.orderSkuItemIds.stream()
          .filter(orderSkuItem -> !consumedOrderSkuItemIds.contains(orderSkuItem))
          .toList();
      var consumed = new Consumed(event.stockOrderRedLeafId, event.orderSkuItemsToStockSckItems);
      var newConsumedStockOrders = Stream.concat(this.consumedStockOrders.stream(), Stream.of(consumed)).toList();

      return new State(orderItemRedLeafId, quantity, availableToBeConsumed, readyToShipAt, backOrderedAt, newOrderSkuItems, newConsumedStockOrders);
    }

    State on(OrderItemReleasedStockSkuItemsEvent event) {
      return this;
    }

    State on(OrderItemSetBackOrderedEvent event) {
      return new State(orderItemRedLeafId, quantity, true, null, Instant.now(), orderSkuItemIds, consumedStockOrders);
    }
  }

  public record OrderItemRedLeafId(String orderId, String skuId) {
    public static OrderItemRedLeafId of(String orderId, String skuId) {
      return new OrderItemRedLeafId(orderId, skuId);
    }

    public String toEntityId() {
      return "%s_%s".formatted(orderId, skuId);
    }
  }

  public record OrderSkuItemId(OrderItemRedLeafId orderItemRedLeafId, UUID orderSkuId) {
    public static OrderSkuItemId of(OrderItemRedLeafId orderItemRedLeafId) {
      return new OrderSkuItemId(orderItemRedLeafId, UUID.randomUUID());
    }
  }

  public record Consumed(StockOrderRedLeafId stockOrderRedLeafId, List<OrderSkuItemToStockSkuItem> orderSkuItemsToStockSkuItems) {}

  public record OrderSkuItemToStockSkuItem(OrderSkuItemId orderSkuItemId, StockSkuItemId stockSkuItemId) {}

  public interface Event {}

  public record OrderItemRedLeafCreateCommand(OrderItemRedLeafId orderItemRedLeafId, int quantity) {}

  public record OrderItemRedLeafCreatedEvent(OrderItemRedLeafId orderItemRedLeafId, int quantity, List<OrderSkuItemId> orderSkuItemIds) implements Event {}

  public record OrderItemRequestsStockSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, List<OrderSkuItemId> orderSkuItemIds) implements Event {}

  public record StockOrderRequestsOrderSkuItemsCommand(OrderItemRedLeafId orderItemRedLeafId, StockOrderRedLeafId stockOrderRedLeafId, List<StockSkuItemId> stockSkuItemIds) {}

  public record StockOrderConsumedOrderSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, StockOrderRedLeafId stockOrderRedLeafId, List<OrderSkuItemToStockSkuItem> orderSkuItemToStockSkuItems) implements Event {}

  public record StockOrderReleaseOrderSkuItemsCommand(OrderItemRedLeafId orderItemRedLeafId, StockOrderRedLeafId stockOrderRedLeafId) {}

  public record StockOrderReleasedOrderSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, StockOrderRedLeafId stockOrderRedLeafId) implements Event {}

  public record OrderItemConsumedStockSkuItemsCommand(OrderItemRedLeafId orderItemRedLeafId, StockOrderRedLeafId stockOrderRedLeafId, List<OrderSkuItemToStockSkuItem> orderSkuItemsToStockSckItems) {}

  public record OrderItemConsumedStockSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, StockOrderRedLeafId stockOrderRedLeafId, List<OrderSkuItemToStockSkuItem> orderSkuItemsToStockSckItems) implements Event {}

  public record OrderItemReleasedStockSkuItemsEvent(OrderItemRedLeafId orderItemRedLeafId, StockOrderRedLeafId stockOrderRedLeafId) implements Event {}

  public record OrderItemSetBackOrderedCommand(OrderItemRedLeafId orderItemRedLeafId) {}

  public record OrderItemSetBackOrderedEvent(OrderItemRedLeafId orderItemRedLeafId) implements Event {}

  // OrderItemRedLeafEntity
  //
  // OrderItemRedLeafCreateCommand
  // --> OrderItemRedLeafCreatedEvent
  // --> OrderItemRequestsStockSkuItemsEvent
  //
  // Action: OrderItemRequestsStockSkuItemsEvent --> OrderItemRequestsStockSkuItemsCommand
  //
  // StockOrderRedLeafEntity
  //
  // OrderItemRequestsStockSkuItemsCommand
  // --> OrderItemConsumedStockSkuItemsEvent
  //
  // Action: OrderItemConsumedStockSkuItemsEvent --> OrderItemConsumedStockSkuItemsCommand
  //
  // OrderItemRedLeafEntity
  //
  // OrderItemConsumedStockSkuItemsCommand
  // --> OrderItemConsumedStockSkuItemsEvent
  // --> OrderItemRequestsStockSkuItemsEvent (optional if more stockSkuItems are needed)
}