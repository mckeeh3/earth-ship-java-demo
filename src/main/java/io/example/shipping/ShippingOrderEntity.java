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
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/ready-to-ship-order-sku-item")
  public Effect<String> readyToShipOrderSkuItem(@RequestBody ReadyToShipOrderSkuItemCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/release-order-sku-item")
  public Effect<String> releaseOrderSkuItem(@RequestBody ReleaseOrderSkuItemCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\nState: {}\nGetShippingOrder", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "ShippingOrder is not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedOrderEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderSkuItemEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderItemEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderSkuItemEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderItemEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String orderId,
      String customerId,
      Instant createdAt,
      Instant readyToShipAt,
      List<OrderItem> orderItems) {

    static State emptyState() {
      return new State(null, null, null, null, List.of());
    }

    boolean isEmpty() {
      return orderId == null || orderId.isEmpty();
    }

    CreatedOrderEvent eventFor(CreateOrderCommand command) {
      return new CreatedOrderEvent(command.orderId(), command.customerId(), command.orderedAt(), toOrderItems(command));
    }

    List<?> eventsFor(ReadyToShipOrderSkuItemCommand command) {
      var events = new ArrayList<>();
      var orderSkuItemReadyToShipEvent = new ReadyToShipOrderSkuItemEvent(command.orderId(), command.orderSkuItemId(), command.skuId(), command.stockSkuItemId(), command.readyToShipAt());
      var newOrderItems = readyToShip(orderSkuItemReadyToShipEvent);
      var newOrderItem = newOrderItems.stream().filter(i -> i.skuId().equals(command.skuId())).findFirst().orElse(null);

      events.add(orderSkuItemReadyToShipEvent);
      if (newOrderItem != null && allOrderSkuItemsReadyToShip(newOrderItem)) {
        events.add(new ReadyToShipOrderItemEvent(command.orderId(), command.skuId(), command.readyToShipAt()));
      }
      if (allOrderItemsReadyToShip(newOrderItems)) {
        events.add(new ReadyToShipOrderEvent(command.orderId(), command.readyToShipAt()));
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
      var orderSkuItemsReleasedEvent = new ReleasedOrderSkuItemEvent(command.orderId(), command.orderSkuItemId(), command.skuId(), command.stockSkuItemId());
      var newOrderItems = release(orderSkuItemsReleasedEvent);
      var newOrderItem = newOrderItems.stream().filter(i -> i.skuId().equals(command.skuId())).findFirst().orElse(null);

      events.add(orderSkuItemsReleasedEvent);
      if (newOrderItem != null && allOrderSkuItemsReleased(newOrderItem)) {
        events.add(new ReleasedOrderItemEvent(command.orderId(), command.skuId()));
      }
      if (allOrderItemsReleased(newOrderItems)) {
        events.add(new ReleasedOrderEvent(command.orderId()));
      }

      return events;
    }

    private boolean allOrderSkuItemsReleased(OrderItem newOrderItem) {
      return newOrderItem.orderSkuItems.stream().allMatch(i -> i.readyToShipAt == null);
    }

    private boolean allOrderItemsReleased(List<OrderItem> newOrderItems) {
      return newOrderItems.stream().allMatch(i -> i.readyToShipAt == null);
    }

    State on(CreatedOrderEvent event) {
      if (isEmpty()) {
        return new State(
            event.orderId(),
            event.customerId(),
            event.orderedAt(),
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
          newOrderItems.stream().allMatch(i -> i.readyToShipAt() != null) ? event.readyToShipAt() : null,
          newOrderItems);
    }

    State on(ReleasedOrderSkuItemEvent event) {
      var newOrderItems = release(event);
      return new State(
          orderId,
          customerId,
          createdAt,
          newOrderItems.stream().allMatch(i -> i.readyToShipAt() == null) ? null : readyToShipAt,
          newOrderItems);
    }

    State on(ReadyToShipOrderItemEvent event) {
      return this;
    }

    State on(ReleasedOrderItemEvent event) {
      return this;
    }

    State on(ReadyToShipOrderEvent event) {
      return this;
    }

    State on(ReleasedOrderEvent event) {
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
                  event.readyToShipAt)
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
          newOrderSkuItems.stream().allMatch(i -> i.readyToShipAt == null) ? null : orderItem.readyToShipAt,
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
                  null)
              : i)
          .toList();
    }
  }

  public record OrderItem(
      String skuId,
      String skuName,
      int quantity,
      Instant readyToShipAt,
      List<OrderSkuItem> orderSkuItems) {}

  public record OrderSkuItem(
      OrderSkuItemId orderSkuItemId,
      String customerId,
      String skuId,
      String skuName,
      StockSkuItemId stockSkuItemId,
      Instant orderedAt,
      Instant readyToShipAt) {}

  public record CreateOrderCommand(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) {}

  public record CreatedOrderEvent(String orderId, String customerId, Instant orderedAt, List<OrderItem> orderItems) {}

  public record ReadyToShipOrderSkuItemCommand(String orderId, OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) {}

  public record ReadyToShipOrderSkuItemEvent(String orderId, OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) {}

  public record ReadyToShipOrderItemEvent(String orderId, String skuId, Instant readyToShipAt) {}

  public record ReadyToShipOrderEvent(String orderId, Instant readyToShipAt) {}

  public record ReleaseOrderSkuItemCommand(String orderId, OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record ReleasedOrderSkuItemEvent(String orderId, OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record ReleasedOrderItemEvent(String orderId, String skuId) {}

  public record ReleasedOrderEvent(String orderId) {}
}
