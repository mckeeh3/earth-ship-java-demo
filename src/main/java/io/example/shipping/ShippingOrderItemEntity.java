package io.example.shipping;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.shipping.OrderSkuItemEntity.OrderSkuItemId;
import io.example.stock.StockSkuItemEntity.StockSkuItemId;
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;

@EntityKey("orderItemId")
@EntityType("shippingOrderItem")
@RequestMapping("/shipping-order-item/{orderItemId}")
public class ShippingOrderItemEntity extends EventSourcedEntity<ShippingOrderItemEntity.State, ShippingOrderItemEntity.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public ShippingOrderItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/create")
  public Effect<String> create(@RequestBody CreateShippingOrderItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isTrue(command.shippingOrderItemId().isEmpty(), "Cannot create Shipping Order Item without shippingOrderItemId")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/ready-to-ship")
  public Effect<String> readyToShip(@RequestBody ReadyToShipOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/release")
  public Effect<String> release(@RequestBody ReleaseOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/back-order")
  public Effect<String> backOrder(@RequestBody BackOrderOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetOrderItem", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Order Item not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedShippingOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReadyToShipOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedOrderItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      ShippingOrderItemId shippingOrderItemId,
      String skuName,
      int quantity,
      String customerId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt,
      List<OrderSkuItem> orderSkuItems) {

    static State emptyState() {
      return new State(null, null, 0, null, null, null, null, null);
    }

    boolean isEmpty() {
      return shippingOrderItemId.isEmpty();
    }

    CreatedShippingOrderItemEvent eventFor(CreateShippingOrderItemCommand command) {
      return new CreatedShippingOrderItemEvent(
          command.shippingOrderItemId(),
          command.skuName(),
          command.quantity(),
          command.customerId(), command.orderedAt(),
          toOrderSkuItems(command));
    }

    List<? extends Event> eventsFor(ReadyToShipOrderSkuItemCommand command) {
      var event = new ReadyToShipOrderSkuItemEvent(command.orderSkuItemId(), command.skuId(), command.stockSkuItemId(), command.readyToShipAt());
      var newState = on(event);
      if (newState.readyToShipAt != null) {
        return List.of(event, new ReadyToShipOrderItemEvent(shippingOrderItemId, newState.readyToShipAt));
      }
      return List.of(event);
    }

    List<? extends Event> eventsFor(ReleaseOrderSkuItemCommand command) {
      var event = new ReleasedOrderSkuItemEvent(command.orderSkuItemId(), command.skuId(), command.stockSkuItemId());
      var newState = on(event);
      if (newState.readyToShipAt != null) {
        return List.of(event, new ReleasedOrderItemEvent(shippingOrderItemId));
      }
      return List.of(event);
    }

    List<? extends Event> eventsFor(BackOrderOrderSkuItemCommand command) {
      var event = new BackOrderedOrderSkuItemEvent(command.orderSkuItemId(), command.skuId(), command.backOrderedAt());
      var newState = on(event);
      if (newState.backOrderedAt != null) {
        return List.of(event, new BackOrderedOrderItemEvent(shippingOrderItemId, newState.backOrderedAt));
      }
      return List.of(event);
    }

    State on(CreatedShippingOrderItemEvent event) {
      return new State(
          event.shippingOrderItemId(),
          event.skuName(),
          event.quantity(),
          customerId,
          orderedAt,
          null,
          null,
          event.orderSkuItems());
    }

    State on(ReadyToShipOrderSkuItemEvent event) {
      var newOrderSkuItems = readyToShip(event);
      var newReadyToShipAt = newOrderSkuItems.stream().allMatch(i -> i.readyToShipAt != null) ? event.readyToShipAt : null;
      var newBackOrderAt = newOrderSkuItems.stream().anyMatch(i -> i.backOrderedAt != null) ? backOrderedAt : null;
      return new State(
          shippingOrderItemId,
          skuName,
          quantity,
          customerId,
          orderedAt,
          newBackOrderAt == null ? newReadyToShipAt : null,
          newBackOrderAt,
          newOrderSkuItems);
    }

    State on(ReleasedOrderSkuItemEvent event) {
      var newOrderSkuItems = release(event);
      return new State(
          shippingOrderItemId,
          skuName,
          quantity,
          customerId,
          orderedAt,
          newOrderSkuItems.stream().anyMatch(i -> i.readyToShipAt == null) ? null : readyToShipAt,
          newOrderSkuItems.stream().anyMatch(i -> i.backOrderedAt != null) ? backOrderedAt : null,
          newOrderSkuItems);
    }

    State on(BackOrderedOrderSkuItemEvent event) {
      var newOrderSkuItems = backOrder(event);
      return new State(
          shippingOrderItemId,
          skuName,
          quantity,
          customerId,
          orderedAt,
          newOrderSkuItems.stream().anyMatch(i -> i.readyToShipAt == null) ? null : readyToShipAt,
          newOrderSkuItems.stream().anyMatch(i -> i.backOrderedAt != null) ? event.backOrderedAt : null,
          newOrderSkuItems);
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

    private List<OrderSkuItem> toOrderSkuItems(CreateShippingOrderItemCommand command) {
      return IntStream.range(0, command.quantity())
          .mapToObj(j -> new OrderSkuItem(
              OrderSkuItemId.of(command.shippingOrderItemId.orderId),
              command.customerId(),
              command.shippingOrderItemId.skuId(),
              command.skuName(),
              null,
              command.orderedAt(),
              null,
              null))
          .toList();
    }

    private List<OrderSkuItem> readyToShip(ReadyToShipOrderSkuItemEvent event) {
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

    private List<OrderSkuItem> release(ReleasedOrderSkuItemEvent event) {
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

    private List<OrderSkuItem> backOrder(BackOrderedOrderSkuItemEvent event) {
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

  public interface Event {}

  public record OrderSkuItem(
      OrderSkuItemId orderSkuItemId,
      String customerId,
      String skuId,
      String skuName,
      StockSkuItemId stockSkuItemId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt) {}

  public record CreateShippingOrderItemCommand(ShippingOrderItemId shippingOrderItemId, String skuName, int quantity, String customerId, Instant orderedAt) {}

  public record CreatedShippingOrderItemEvent(ShippingOrderItemId shippingOrderItemId, String skuName, int quantity, String customerId, Instant orderedAt, List<OrderSkuItem> orderSkuItems) implements Event {}

  public record ReadyToShipOrderSkuItemCommand(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) {}

  public record ReadyToShipOrderSkuItemEvent(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) implements Event {}

  public record ReadyToShipOrderItemEvent(ShippingOrderItemId shippingOrderItemId, Instant readyToShipAt) implements Event {}

  public record ReleaseOrderSkuItemCommand(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record ReleasedOrderSkuItemEvent(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) implements Event {}

  public record ReleasedOrderItemEvent(ShippingOrderItemId shippingOrderItemId) implements Event {}

  public record BackOrderOrderSkuItemCommand(OrderSkuItemId orderSkuItemId, String skuId, Instant backOrderedAt) {}

  public record BackOrderedOrderSkuItemEvent(OrderSkuItemId orderSkuItemId, String skuId, Instant backOrderedAt) implements Event {}

  public record BackOrderedOrderItemEvent(ShippingOrderItemId shippingOrderItemId, Instant backOrderedAt) implements Event {}

  public record ShippingOrderItemId(String orderId, String skuId) {
    public static ShippingOrderItemId of(String orderId, String skuId) {
      return new ShippingOrderItemId(orderId, skuId);
    }

    public String toEntityId() {
      return "%s_%s".formatted(orderId, skuId);
    }

    public boolean isEmpty() {
      return orderId == null || skuId == null;
    }
  }
}
