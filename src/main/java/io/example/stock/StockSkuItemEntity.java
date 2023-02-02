package io.example.stock;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.shipping.OrderSkuItemId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("stockSkuItemEntityId")
@EntityType("stock-sku-item")
@RequestMapping("/stock-sku-item/{stockSkuItemEntityId}")
public class StockSkuItemEntity extends EventSourcedEntity<StockSkuItemEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemEntity.class);
  private final String entityId;

  public StockSkuItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateStockSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    if (currentState().isEmpty()) {
      return effects()
          .emitEvents(currentState().eventsFor(command))
          .thenReply(__ -> "OK");
    }
    return effects().reply("OK");
  }

  @PutMapping("/order-requests-join-to-stock")
  public Effect<String> orderRequestsJoinToStock(@RequestBody OrderRequestsJoinToStockCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/stock-requests-join-to-order-accepted")
  public Effect<String> stockRequestsJoinToOrderAccepted(@RequestBody StockRequestsJoinToOrderAcceptedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/stock-requests-join-to-order-rejected")
  public Effect<String> stockRequestsJoinToOrderRejected(@RequestBody StockRequestsJoinToOrderRejectedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/order-requests-join-to-stock-released")
  public Effect<String> orderRequestsJoinToStockReleased(@RequestBody OrderRequestsJoinToStockReleasedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}", entityId, currentState());
    return effects().reply(currentState());
  }

  @EventHandler
  public State on(CreatedStockSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockRejectedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockReleasedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderReleasedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      StockSkuItemId stockSkuItemId,
      String skuId,
      String skuName,
      OrderSkuItemId orderSkuItemId,
      Instant readyToShipAt) {

    static State emptyState() {
      return new State(null, null, null, null, null);
    }

    boolean isEmpty() {
      return stockSkuItemId == null;
    }

    List<?> eventsFor(CreateStockSkuItemCommand command) {
      return List.of(
          new CreatedStockSkuItemEvent(
              command.stockSkuItemId,
              command.skuId,
              command.skuName),
          new StockRequestedJoinToOrderEvent(
              command.stockSkuItemId,
              command.skuId));
    }

    Object eventFor(OrderRequestsJoinToStockCommand command) {
      if (orderSkuItemId == null || orderSkuItemId.equals(command.orderSkuItemId)) {
        return new OrderRequestedJoinToStockAcceptedEvent(
            command.stockSkuItemId,
            command.skuId,
            command.orderSkuItemId,
            Instant.now());
      } else {
        return new OrderRequestedJoinToStockRejectedEvent(
            command.stockSkuItemId,
            command.skuId,
            command.orderSkuItemId);
      }
    }

    List<?> eventsFor(StockRequestsJoinToOrderAcceptedCommand command) {
      if (orderSkuItemId == null) {
        return List.of((new StockRequestedJoinToOrderAcceptedEvent(
            command.stockSkuItemId,
            command.skuId,
            command.orderSkuItemId,
            command.readyToShipAt)));
      } else {
        return List.of(
            new StockRequestedJoinToOrderEvent(
                command.stockSkuItemId,
                command.skuId),
            new StockRequestedJoinToOrderReleasedEvent(
                command.stockSkuItemId,
                command.skuId,
                command.orderSkuItemId));
      }
    }

    StockRequestedJoinToOrderEvent eventFor(StockRequestsJoinToOrderRejectedCommand command) {
      return new StockRequestedJoinToOrderEvent(
          command.stockSkuItemId,
          command.skuId);
    }

    OrderRequestedJoinToStockReleasedEvent eventFor(OrderRequestsJoinToStockReleasedCommand command) {
      return new OrderRequestedJoinToStockReleasedEvent(
          command.stockSkuItemId,
          command.skuId,
          command.orderSkuItemId);
    }

    State on(CreatedStockSkuItemEvent event) {
      return new State(
          event.stockSkuItemId,
          event.skuId,
          event.skuName,
          null,
          null);
    }

    State on(StockRequestedJoinToOrderEvent event) {
      return this;
    }

    State on(OrderRequestedJoinToStockAcceptedEvent event) {
      return new State(
          stockSkuItemId,
          skuId,
          skuName,
          event.orderSkuItemId,
          event.readyToShipAt);
    }

    State on(OrderRequestedJoinToStockRejectedEvent event) {
      if (event.orderSkuItemId.equals(orderSkuItemId)) {
        return new State(
            stockSkuItemId,
            skuId,
            skuName,
            null,
            null);
      }
      return this;
    }

    State on(StockRequestedJoinToOrderAcceptedEvent event) {
      return new State(
          stockSkuItemId,
          skuId,
          skuName,
          event.orderSkuItemId,
          event.readyToShipAt);
    }

    State on(OrderRequestedJoinToStockReleasedEvent event) {
      if (event.orderSkuItemId.equals(orderSkuItemId)) {
        return new State(
            stockSkuItemId,
            skuId,
            skuName,
            null,
            null);
      }
      return this;
    }

    State on(StockRequestedJoinToOrderReleasedEvent event) {
      if (event.orderSkuItemId.equals(orderSkuItemId)) {
        return new State(
            stockSkuItemId,
            skuId,
            skuName,
            null,
            null);
      }
      return this;
    }
  }

  public record CreateStockSkuItemCommand(StockSkuItemId stockSkuItemId, String skuId, String skuName) {}

  public record CreatedStockSkuItemEvent(StockSkuItemId stockSkuItemId, String skuId, String skuName) {}

  public record StockRequestedJoinToOrderEvent(StockSkuItemId stockSkuItemId, String skuId) {}

  public record OrderRequestsJoinToStockCommand(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId) {}

  public record OrderRequestedJoinToStockAcceptedEvent(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId, Instant readyToShipAt) {}

  public record OrderRequestedJoinToStockRejectedEvent(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId) {}

  public record StockRequestsJoinToOrderAcceptedCommand(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId, Instant readyToShipAt) {}

  public record StockRequestedJoinToOrderAcceptedEvent(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId, Instant readyToShipAt) {}

  public record StockRequestsJoinToOrderRejectedCommand(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId) {}

  public record OrderRequestsJoinToStockReleasedCommand(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId) {}

  public record OrderRequestedJoinToStockReleasedEvent(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId) {}

  public record StockRequestedJoinToOrderReleasedEvent(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId) {}
}
