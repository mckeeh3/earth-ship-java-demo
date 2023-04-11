package io.example.shipping;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.stock.StockSkuItemId;
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;

@EntityKey("orderSkuItemId")
@EntityType("orderSkuItem")
@RequestMapping("/order-sku-item/{orderSkuItemId}")
public class OrderSkuItemEntity extends EventSourcedEntity<OrderSkuItemEntity.State, OrderSkuItemEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(OrderSkuItemEntity.class);
  private final String entityId;

  public OrderSkuItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/create")
  public Effect<String> create(@RequestBody CreateOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    if (currentState().isEmpty()) {
      return effects()
          .emitEvents(currentState().eventsFor(command))
          .thenReply(__ -> "OK");
    }
    return effects().reply("OK");
  }

  @PutMapping("/stock-requests-join-to-order")
  public Effect<String> stockRequestsJoinToOrder(@RequestBody StockRequestsJoinToOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/order-requests-join-to-stock-accepted")
  public Effect<String> orderRequestedJoinToStockAccepted(@RequestBody OrderRequestsJoinToStockAcceptedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/order-requests-join-to-stock-rejected")
  public Effect<String> orderRequestedJoinToStockRejected(@RequestBody OrderRequestsJoinToStockRejectedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/stock-requests-join-to-order-released")
  public Effect<String> stockRequestsJoinToOrderReleased(@RequestBody StockRequestsJoinToOrderReleasedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/back-order-order-sku-item")
  public Effect<String> backOrderRequested(@RequestBody BackOrderOrderSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetOrderSkuItem", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "OrderSkuItem '%s' not found".formatted(entityId))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderRejectedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderReleasedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockReleasedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(BackOrderedOrderSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      OrderSkuItemId orderSkuItemId,
      String skuId,
      String skuName,
      String customerId,
      StockSkuItemId stockSkuItemId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt) {

    static State emptyState() {
      return new State(null, null, null, null, null, null, null, null);
    }

    boolean isEmpty() {
      return orderSkuItemId == null;
    }

    List<? extends Event> eventsFor(CreateOrderSkuItemCommand command) {
      if (!isEmpty()) {
        return List.of(); // already created
      }

      return List.of(
          new CreatedOrderSkuItemEvent(
              command.orderSkuItemId,
              command.customerId,
              command.skuId,
              command.skuName,
              command.orderedAt),
          new OrderRequestedJoinToStockEvent(
              command.orderSkuItemId,
              command.skuId));
    }

    List<? extends Event> eventsFor(OrderRequestsJoinToStockAcceptedCommand command) {
      if (command.stockSkuItemId.equals(stockSkuItemId)) {
        return List.of(); // already joined
      }

      if (stockSkuItemId == null) {
        return List.of(
            new OrderRequestedJoinToStockAcceptedEvent(
                command.orderSkuItemId,
                orderedAt,
                command.skuId,
                command.stockSkuItemId,
                Instant.now()));
      }

      return List.of(
          new OrderRequestedJoinToStockEvent(
              command.orderSkuItemId,
              command.skuId),
          new OrderRequestedJoinToStockReleasedEvent(
              command.orderSkuItemId,
              command.skuId,
              command.stockSkuItemId));
    }

    OrderRequestedJoinToStockEvent eventFor(OrderRequestsJoinToStockRejectedCommand command) {
      return new OrderRequestedJoinToStockEvent(
          command.orderSkuItemId,
          command.skuId);
    }

    Event eventFor(StockRequestsJoinToOrderCommand command) {
      if (stockSkuItemId == null || stockSkuItemId.equals(command.stockSkuItemId)) {
        return new StockRequestedJoinToOrderAcceptedEvent(
            command.orderSkuItemId,
            orderedAt,
            command.skuId,
            command.stockSkuItemId,
            Instant.now());
      } else {
        return new StockRequestedJoinToOrderRejectedEvent(
            command.orderSkuItemId,
            command.skuId,
            command.stockSkuItemId);
      }
    }

    StockRequestedJoinToOrderReleasedEvent eventsFor(StockRequestsJoinToOrderReleasedCommand command) {
      return new StockRequestedJoinToOrderReleasedEvent(
          command.orderSkuItemId,
          command.skuId,
          command.stockSkuItemId);
    }

    BackOrderedOrderSkuItemEvent eventFor(BackOrderOrderSkuItemCommand command) {
      return new BackOrderedOrderSkuItemEvent(
          command.orderSkuItemId,
          orderedAt,
          command.skuId,
          backOrderedAt == null ? Instant.now() : backOrderedAt);
    }

    State on(CreatedOrderSkuItemEvent event) {
      if (!isEmpty()) {
        return this;
      }
      return new State(
          event.orderSkuItemId,
          event.skuId,
          event.skuName,
          event.customerId,
          null,
          event.orderedAt,
          null,
          null);
    }

    State on(OrderRequestedJoinToStockEvent event) {
      return this;
    }

    State on(OrderRequestedJoinToStockAcceptedEvent event) {
      return new State(
          orderSkuItemId,
          skuId,
          skuName,
          customerId,
          event.stockSkuItemId,
          orderedAt,
          event.readyToShipAt,
          null);
    }

    State on(StockRequestedJoinToOrderAcceptedEvent event) {
      return new State(
          orderSkuItemId,
          skuId,
          skuName,
          customerId,
          event.stockSkuItemId,
          orderedAt,
          event.readyToShipAt,
          null);
    }

    State on(StockRequestedJoinToOrderRejectedEvent event) {
      if (event.stockSkuItemId.equals(stockSkuItemId)) {
        return new State(
            orderSkuItemId,
            skuId,
            skuName,
            customerId,
            null,
            orderedAt,
            null,
            null);
      }
      return this;
    }

    State on(StockRequestedJoinToOrderReleasedEvent event) {
      if (event.stockSkuItemId.equals(stockSkuItemId)) {
        return new State(
            orderSkuItemId,
            skuId,
            skuName,
            customerId,
            null,
            orderedAt,
            null,
            null);
      }
      return this;
    }

    State on(OrderRequestedJoinToStockReleasedEvent event) {
      if (event.stockSkuItemId.equals(stockSkuItemId)) {
        return new State(
            orderSkuItemId,
            skuId,
            skuName,
            customerId,
            null,
            orderedAt,
            null,
            null);
      }
      return this;
    }

    State on(BackOrderedOrderSkuItemEvent event) {
      return new State(
          orderSkuItemId,
          skuId,
          skuName,
          customerId,
          null,
          orderedAt,
          null,
          event.backOrderedAt);
    }
  }

  public interface Event {}

  public record CreateOrderSkuItemCommand(OrderSkuItemId orderSkuItemId, String customerId, String skuId, String skuName, Instant orderedAt) {}

  public record CreatedOrderSkuItemEvent(OrderSkuItemId orderSkuItemId, String customerId, String skuId, String skuName, Instant orderedAt) implements Event {}

  public record OrderRequestedJoinToStockEvent(OrderSkuItemId orderSkuItemId, String skuId) implements Event {}

  public record StockRequestsJoinToOrderCommand(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record StockRequestedJoinToOrderAcceptedEvent(OrderSkuItemId orderSkuItemId, Instant orderedAt, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) implements Event {}

  public record StockRequestedJoinToOrderRejectedEvent(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) implements Event {}

  public record OrderRequestsJoinToStockAcceptedCommand(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record OrderRequestedJoinToStockAcceptedEvent(OrderSkuItemId orderSkuItemId, Instant orderedAt, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) implements Event {}

  public record OrderRequestsJoinToStockRejectedCommand(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record StockRequestsJoinToOrderReleasedCommand(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record StockRequestedJoinToOrderReleasedEvent(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) implements Event {}

  public record OrderRequestedJoinToStockReleasedEvent(OrderSkuItemId orderSkuItemId, String skuId, StockSkuItemId stockSkuItemId) implements Event {}

  public record BackOrderOrderSkuItemCommand(OrderSkuItemId orderSkuItemId, String skuId) {}

  public record BackOrderedOrderSkuItemEvent(OrderSkuItemId orderSkuItemId, Instant orderedAt, String skuId, Instant backOrderedAt) implements Event {}
}
