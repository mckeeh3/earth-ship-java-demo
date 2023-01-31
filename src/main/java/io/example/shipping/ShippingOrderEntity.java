package io.example.shipping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.stock.StockSkuItemId;
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("orderId")
@EntityType("shippingOrder")
@RequestMapping("/shipping-order/{orderId}")
public class ShippingOrderEntity extends EventSourcedEntity<ShippingOrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrderEntity.class);
  private final String entityId;

  public ShippingOrderEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/create")
  public Effect<String> createOrder(@RequestBody CreateOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/ready-to-ship-order-sku-item")
  public Effect<String> readyToShipOrderSkuItem(@RequestBody ReadyToShipOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/release-order-sku-item")
  public Effect<String> releaseOrderSkuItem(@RequestBody ReleaseOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/back-order-order-sku-item")
  public Effect<String> backOrderOrderSkuItem(@RequestBody BackOrderOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetShippingOrder", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "ShippingOrder is not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String orderId,
      String customerId,
      Instant createdAt,
      Instant readyToShipAt,
      Instant backOrderedAt,
      List<OrderItem> orderItems) {

    static State emptyState() {
      return new State(null, null, null, null, null, List.of());
    }

    boolean isEmpty() {
      return orderId == null || orderId.isEmpty();
    }

    CreatedOrderEvent eventFor(CreateOrderCommand command) {
      return new CreatedOrderEvent(command.orderId(), command.customerId(), command.orderedAt(), toOrderItems(command));
    }

    List<?> eventsFor(ReadyToShipOrderSkuItemCommand command) {
      var events = new ArrayList<>();
      var orderSkuItemReadyToShipEvent = new ReadyToShipOrderSkuItemEvent(command.orderSkuItemId(), command.skuId(), command.stockSkuItemId(), command.readyToShipAt());
      events.add(orderSkuItemReadyToShipEvent);

      var newOrderItems = readyToShip(orderSkuItemReadyToShipEvent);
      var newOrderItem = newOrderItems.stream().filter(i -> i.skuId().equals(command.skuId())).findFirst().orElse(null);

      if (newOrderItem != null && allOrderSkuItemsReadyToShip(newOrderItem)) {
        events.add(new ReadyToShipOrderItemEvent(command.orderSkuItemId().orderId(), command.skuId(), command.readyToShipAt()));
      }
      if (allOrderItemsReadyToShip(newOrderItems)) {
        events.add(new ReadyToShipOrderEvent(command.orderSkuItemId().orderId(), command.readyToShipAt()));
      }

      return events;
    }

    private boolean allOrderSkuItemsReadyToShip(OrderItem newOrderItem) {
      return newOrderItem.orderSkuItems.stream().allMatch(i -> i.readyToShipAt != null);
    }

    private boolean allOrderItemsReadyToShip(List<OrderItem> newOrderItems) {
      return newOrderItems.stream().allMatch(i -> i.readyToShipAt != null);
    }

    List<?> eventsFor(ReleaseOrderSkuItemCommand command) {
      var events = new ArrayList<>();
      var orderSkuItemsReleasedEvent = new ReleasedOrderSkuItemEvent(command.orderSkuItemId(), command.skuId(), command.stockSkuItemId());
      events.add(orderSkuItemsReleasedEvent);

      var newOrderItems = release(orderSkuItemsReleasedEvent);
      var newOrderItem = newOrderItems.stream().filter(i -> i.skuId().equals(command.skuId())).findFirst().orElse(null);

      if (newOrderItem != null && allOrderSkuItemsReleased(newOrderItem)) {
        events.add(new ReleasedOrderItemEvent(command.orderSkuItemId().orderId(), command.skuId()));
      }
      if (allOrderItemsReleased(newOrderItems)) {
        events.add(new ReleasedOrderEvent(command.orderSkuItemId().orderId()));
      }

      return events;
    }

    private boolean allOrderSkuItemsReleased(OrderItem newOrderItem) {
      return newOrderItem.orderSkuItems.stream().allMatch(i -> i.readyToShipAt == null);
    }

    private boolean allOrderItemsReleased(List<OrderItem> newOrderItems) {
      return newOrderItems.stream().allMatch(i -> i.readyToShipAt == null);
    }

    List<?> eventsFor(BackOrderOrderSkuItemCommand command) {
      var events = new ArrayList<>();
      var orderSkuItemBackOrderedEvent = new BackOrderedOrderSkuItemEvent(command.orderSkuItemId(), command.skuId(), command.backOrderedAt());
      events.add(orderSkuItemBackOrderedEvent);

      var newOrderItems = backOrder(orderSkuItemBackOrderedEvent);
      var newOrderItem = newOrderItems.stream().filter(i -> i.skuId().equals(command.skuId())).findFirst().orElse(null);

      if (newOrderItem != null && anyOrderSkuItemsBackOrdered(newOrderItem)) {
        events.add(new BackOrderedOrderItemEvent(command.orderSkuItemId().orderId(), command.skuId(), command.backOrderedAt()));
      }
      if (anyOrderItemsBackOrdered(newOrderItems)) {
        events.add(new BackOrderedOrderEvent(command.orderSkuItemId().orderId(), command.backOrderedAt()));
      }

      return events;
    }

    private boolean anyOrderSkuItemsBackOrdered(OrderItem newOrderItem) {
      return newOrderItem.orderSkuItems.stream().anyMatch(i -> i.backOrderedAt != null);
    }

    private boolean anyOrderItemsBackOrdered(List<OrderItem> newOrderItems) {
      return newOrderItems.stream().anyMatch(i -> i.backOrderedAt != null);
    }

    State on(CreatedOrderEvent event) {
      if (isEmpty()) {
        return new State(
            event.orderId(),
            event.customerId(),
            event.orderedAt(),
            null,
            null,
            event.orderItems());
      } else {
        return this;
      }
    }

    State on(ReadyToShipOrderSkuItemEvent event) {
      var newOrderItems = readyToShip(event);
      return new State(
          orderId,
          customerId,
          createdAt,
          newOrderItems.stream().anyMatch(i -> i.readyToShipAt() != null) ? event.readyToShipAt() : null,
          newOrderItems.stream().allMatch(i -> i.backOrderedAt() != null) ? backOrderedAt : null,
          newOrderItems);
    }

    State on(ReleasedOrderSkuItemEvent event) {
      var newOrderItems = release(event);
      return new State(
          orderId,
          customerId,
          createdAt,
          newOrderItems.stream().anyMatch(i -> i.readyToShipAt() == null) ? null : readyToShipAt,
          newOrderItems.stream().allMatch(i -> i.backOrderedAt() != null) ? backOrderedAt : null,
          newOrderItems);
    }

    State on(BackOrderedOrderSkuItemEvent event) {
      var newOrderItems = backOrder(event);
      return new State(
          orderId,
          customerId,
          createdAt,
          newOrderItems.stream().anyMatch(i -> i.readyToShipAt() == null) ? null : readyToShipAt,
          newOrderItems.stream().allMatch(i -> i.backOrderedAt() != null) ? event.backOrderedAt() : null,
          newOrderItems);
    }

    State on(ReadyToShipOrderItemEvent event) {
      return this;
    }

    State on(ReleasedOrderItemEvent event) {
      return this;
    }

    State on(BackOrderedOrderItemEvent event) {
      return this;
    }

    State on(ReadyToShipOrderEvent event) {
      return this;
    }

    State on(ReleasedOrderEvent event) {
      return this;
    }

    State on(BackOrderedOrderEvent event) {
      return this;
    }

    OrderItem findOrderItem(String skuId) {
      return orderItems.stream().filter(item -> item.skuId().equals(skuId)).findFirst().orElse(null);
    }

    private List<OrderItem> toOrderItems(CreateOrderCommand command) {
      return command.orderItems().stream()
          .map(i -> new OrderItem(
              i.skuId(),
              i.skuName(),
              i.quantity(),
              null,
              null,
              toOrderSkuItems(command, i)))
          .toList();
    }

    private List<OrderSkuItem> toOrderSkuItems(CreateOrderCommand command, OrderItem orderItem) {
      return IntStream.range(0, orderItem.quantity())
          .mapToObj(j -> new OrderSkuItem(
              OrderSkuItemId.of(command.orderId),
              command.customerId(),
              orderItem.skuId(),
              orderItem.skuName(),
              null,
              command.orderedAt(),
              null,
              null))
          .toList();
    }

    private List<OrderItem> readyToShip(ReadyToShipOrderSkuItemEvent event) {
      return orderItems.stream()
          .map(i -> i.skuId().equals(event.skuId)
              ? readyToShip(event, i)
              : i)
          .toList();
    }

    private OrderItem readyToShip(ReadyToShipOrderSkuItemEvent event, OrderItem orderItem) {
      var newOrderSkuItems = readyToShip(event, orderItem.orderSkuItems);
      return new OrderItem(
          orderItem.skuId(),
          orderItem.skuName(),
          orderItem.quantity(),
          newOrderSkuItems.stream().allMatch(i -> i.readyToShipAt != null) ? event.readyToShipAt : null,
          newOrderSkuItems.stream().anyMatch(i -> i.backOrderedAt != null) ? orderItem.backOrderedAt : null,
          newOrderSkuItems);
    }

    private List<OrderSkuItem> readyToShip(ReadyToShipOrderSkuItemEvent event, List<OrderSkuItem> orderSkuItems) {
      return orderSkuItems.stream()
          .map(i -> i.orderSkuItemId().equals(event.orderSkuItemId)
              ? new OrderSkuItem(
                  i.orderSkuItemId(),
                  i.customerId(),
                  i.skuId(),
                  i.skuName(),
                  event.stockSkuItemId(),
                  i.orderedAt(),
                  event.readyToShipAt,
                  null)
              : i)
          .toList();
    }

    private List<OrderItem> release(ReleasedOrderSkuItemEvent event) {
      return orderItems.stream()
          .map(i -> i.skuId().equals(event.skuId)
              ? release(event, i)
              : i)
          .toList();
    }

    private OrderItem release(ReleasedOrderSkuItemEvent event, OrderItem orderItem) {
      var newOrderSkuItems = release(event, orderItem.orderSkuItems);
      return new OrderItem(
          orderItem.skuId(),
          orderItem.skuName(),
          orderItem.quantity(),
          newOrderSkuItems.stream().anyMatch(i -> i.readyToShipAt == null) ? null : orderItem.readyToShipAt,
          newOrderSkuItems.stream().anyMatch(i -> i.backOrderedAt != null) ? orderItem.backOrderedAt : null,
          orderItem.orderSkuItems());
    }

    private List<OrderSkuItem> release(ReleasedOrderSkuItemEvent event, List<OrderSkuItem> orderSkuItems) {
      return orderSkuItems.stream()
          .map(i -> i.orderSkuItemId().equals(event.orderSkuItemId)
              ? new OrderSkuItem(
                  i.orderSkuItemId(),
                  i.customerId(),
                  i.skuId(),
                  i.skuName(),
                  i.stockSkuItemId(),
                  i.orderedAt(),
                  null,
                  null)
              : i)
          .toList();
    }

    private List<OrderItem> backOrder(BackOrderedOrderSkuItemEvent event) {
      return orderItems.stream()
          .map(i -> i.skuId().equals(event.skuId)
              ? backOrder(event, i)
              : i)
          .toList();
    }

    private OrderItem backOrder(BackOrderedOrderSkuItemEvent event, OrderItem orderItem) {
      var newOrderSkuItems = backOrder(event, orderItem.orderSkuItems);
      return new OrderItem(
          orderItem.skuId(),
          orderItem.skuName(),
          orderItem.quantity(),
          newOrderSkuItems.stream().anyMatch(i -> i.readyToShipAt == null) ? null : orderItem.readyToShipAt,
          newOrderSkuItems.stream().anyMatch(i -> i.backOrderedAt != null) ? event.backOrderedAt : null,
          newOrderSkuItems);
    }

    private List<OrderSkuItem> backOrder(BackOrderedOrderSkuItemEvent event, List<OrderSkuItem> orderSkuItems) {
      return orderSkuItems.stream()
          .map(i -> i.orderSkuItemId().equals(event.orderSkuItemId)
              ? new OrderSkuItem(
                  i.orderSkuItemId(),
                  i.customerId(),
                  i.skuId(),
                  i.skuName(),
                  i.stockSkuItemId(),
                  i.orderedAt(),
                  null,
                  event.backOrderedAt)
              : i)
          .toList();
    }
  }

  public record OrderItem(
      String skuId,
      String skuName,
      int quantity,
      Instant readyToShipAt,
      Instant backOrderedAt,
      List<OrderSkuItem> orderSkuItems) {}

  public record OrderSkuItem(
      OrderSkuItemId orderSkuItemId,
      String customerId,
      String skuId,
      String skuName,
      StockSkuItemId stockSkuItemId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt) {}

  public record CreateOrderCommand(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) {}

  public record CreatedOrderEvent(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) {}

  public record ReadyToShipOrderSkuItemCommand(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) {}

  public record ReadyToShipOrderSkuItemEvent(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) {}

  public record ReadyToShipOrderItemEvent(String orderId, String skuId, Instant readyToShipAt) {}

  public record ReadyToShipOrderEvent(String orderId, Instant readyToShipAt) {}

  public record ReleaseOrderSkuItemCommand(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record ReleasedOrderSkuItemEvent(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record ReleasedOrderItemEvent(String orderId, String skuId) {}

  public record ReleasedOrderEvent(String orderId) {}

  public record BackOrderOrderSkuItemCommand(OrderSkuItemId orderSkuItemId, String skuId, Instant backOrderedAt) {}

  public record BackOrderedOrderSkuItemEvent(OrderSkuItemId orderSkuItemId, String skuId, Instant backOrderedAt) {}

  public record BackOrderedOrderItemEvent(String orderId, String skuId, Instant backOrderedAt) {}

  public record BackOrderedOrderEvent(String orderId, Instant backOrderedAt) {}
}
