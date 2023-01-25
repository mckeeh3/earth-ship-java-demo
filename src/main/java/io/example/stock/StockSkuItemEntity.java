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

import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("stockSkuItemId")
@EntityType("stockSkuItem")
@RequestMapping("/stock-sku-item/{stockSkuItemId}")
public class StockSkuItemEntity extends EventSourcedEntity<StockSkuItemEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemEntity.class);
  private final String entityId;

  public StockSkuItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @PostMapping("/create")
  Effect<String> create(@RequestBody CreateStockSkuItemCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    if (currentState().isEmpty()) {
      return effects()
          .emitEvents(currentState().eventsFor(command))
          .thenReply(__ -> "OK");
    }
    return effects().reply("OK");
  }

  @PutMapping("/order-requests-join-to-stock")
  Effect<String> orderRequestsJoinToStock(@RequestBody OrderRequestsJoinToStockCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/order-requests-join-to-stock-rejected")
  Effect<String> orderRequestsJoinToStockRejected(@RequestBody OrderRequestsJoinToStockRejectedCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/stock-requests-join-to-order-accepted")
  Effect<String> stockRequestsJoinToOrderAccepted(@RequestBody StockRequestsJoinToOrderAcceptedCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/stock-requests-join-to-order-rejected")
  Effect<String> stockRequestsJoinToOrderRejected(@RequestBody StockRequestsJoinToOrderRejectedCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  Effect<State> get() {
    log.info("EntityId: {}\nState: {}", entityId, currentState());
    return effects().reply(currentState());
  }

  @EventHandler
  public State on(CreatedStockSkuItemEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockRejectedEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockRequestedJoinToOrderRejectedEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String stockSkuItemId,
      String skuId,
      String skuName,
      String orderId,
      String orderSkuItemId,
      String stockOrderId,
      Instant readyToShipAt) {

    State empState() {
      return new State(null, null, null, null, null, null, null);
    }

    boolean isEmpty() {
      return stockSkuItemId == null || stockSkuItemId.isBlank();
    }

    List<?> eventsFor(CreateStockSkuItemCommand command) {
      return List.of(
          new CreatedStockSkuItemEvent(command.stockSkuItemId, command.skuId, command.skuName, command.stockOrderId),
          new StockRequestedJoinToOrderEvent(command.stockSkuItemId, command.skuId, command.stockOrderId));
    }

    Object eventFor(OrderRequestsJoinToStockCommand command) {
      if (orderSkuItemId == null || orderSkuItemId.equals(command.orderSkuItemId)) {
        return new OrderRequestedJoinToStockAcceptedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId);
      } else {
        return new OrderRequestedJoinToStockRejectedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId);
      }
    }

    OrderRequestedJoinToStockRejectedEvent eventFor(OrderRequestsJoinToStockRejectedCommand command) {
      return new OrderRequestedJoinToStockRejectedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId);
    }

    List<?> eventsFor(StockRequestsJoinToOrderAcceptedCommand command) {
      if (orderSkuItemId == null) {
        return List.of((new StockRequestedJoinToOrderAcceptedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId, command.readyToShipAt)));
      } else {
        return List.of(
            new StockRequestedJoinToOrderEvent(command.stockSkuItemId, command.skuId, command.stockOrderId),
            new StockRequestedJoinToOrderRejectedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId));
      }
    }

    StockRequestedJoinToOrderEvent eventFor(StockRequestsJoinToOrderRejectedCommand command) {
      return new StockRequestedJoinToOrderEvent(command.stockSkuItemId, command.skuId, command.stockOrderId);
    }

    State on(CreatedStockSkuItemEvent event) {
      return new State(event.stockSkuItemId, event.skuId, event.skuName, null, null, event.stockOrderId, null);
    }

    State on(StockRequestedJoinToOrderEvent event) {
      return this;
    }

    State on(OrderRequestedJoinToStockAcceptedEvent event) {
      return new State(stockSkuItemId, skuId, skuName, event.orderId, event.orderSkuItemId, stockOrderId, null);
    }

    State on(OrderRequestedJoinToStockRejectedEvent event) {
      return new State(stockSkuItemId, skuId, skuName, null, null, stockOrderId, null);
    }

    State on(StockRequestedJoinToOrderAcceptedEvent event) {
      return new State(stockSkuItemId, skuId, skuName, event.orderId, event.orderSkuItemId, stockOrderId, event.readyToShipAt);
    }

    State on(StockRequestedJoinToOrderRejectedEvent event) {
      return new State(stockSkuItemId, skuId, skuName, null, null, stockOrderId, null);
    }
  }

  public record CreateStockSkuItemCommand(String stockSkuItemId, String skuId, String skuName, String stockOrderId) {}

  public record CreatedStockSkuItemEvent(String stockSkuItemId, String skuId, String skuName, String stockOrderId) {}

  public record StockRequestedJoinToOrderEvent(String stockSkuItemId, String skuId, String stockOrderId) {}

  public record OrderRequestsJoinToStockCommand(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record OrderRequestedJoinToStockAcceptedEvent(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record OrderRequestsJoinToStockRejectedCommand(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record OrderRequestedJoinToStockRejectedEvent(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record StockRequestsJoinToOrderAcceptedCommand(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId, Instant readyToShipAt) {}

  public record StockRequestedJoinToOrderAcceptedEvent(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId, Instant readyToShipAt) {}

  public record StockRequestsJoinToOrderRejectedCommand(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record StockRequestedJoinToOrderRejectedEvent(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}
}
