package io.example.shipping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("orderSkuItemId")
@EntityType("orderSkuItem")
@RequestMapping("/order-sku-item/{orderSkuItemId}")
public class OrderSkuItemEntity extends EventSourcedEntity<OrderSkuItemEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(OrderSkuItemEntity.class);
  private final String entityId;

  public OrderSkuItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateOrderSkuItemCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/order-requested-join-to-stock-accepted")
  public Effect<String> orderRequestedJoinToStockAccepted(@RequestBody OrderRequestedJoinToStockAcceptedCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/order-requested-join-to-stock-rejected")
  public Effect<String> orderRequestedJoinToStockRejected(@RequestBody OrderRequestedJoinToStockRejectedCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/stock-requests-join-to-order")
  public Effect<String> stockRequestsJoinToOrder(@RequestBody StockRequestsJoinToOrderCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/stock-requests-join-to-order-rejected")
  public Effect<String> stockRequestsJoinToOrderRejected(@RequestBody StockRequestsJoinToOrderRejectedCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/back-order-requested")
  public Effect<String> backOrderRequested(@RequestBody BackOrderSkuItemCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\nState: {}\nGetOrderSkuItem", entityId, currentState());
    return Validator.<State>start()
        .isTrue(currentState().isEmpty(), "OrderSkuItem not found")
        .ifErrorOrElse(
            errorMessage -> effects().error(errorMessage),
            () -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedOrderSkuItemEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderRequestedJoinToStockEvent event) {
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

  @EventHandler
  public State on(BackOrderedSkuItemEvent event) {
    log.info("EntityId: {}\nState: {}\nEvent: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String customerId,
      String orderId,
      String orderSkuItemId,
      String skuId,
      String skuName,
      String stockSkuItemId,
      String stockOrderId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt) {

    static State emptyState() {
      return new State(null, null, null, null, null, null, null, null, null, null);
    }

    boolean isEmpty() {
      return orderSkuItemId == null;
    }

    List<?> eventsFor(CreateOrderSkuItemCommand command) {
      var events = new ArrayList<>();
      events.add(new CreatedOrderSkuItemEvent(command.orderSkuItemId, command.customerId, command.orderId, command.skuId, command.skuName, command.orderedAt));
      events.add(new OrderRequestedJoinToStockEvent(command.orderSkuItemId, command.orderId, command.skuId));

      return events;
    }

    List<?> eventsFor(OrderRequestedJoinToStockAcceptedCommand command) {
      var events = new ArrayList<>();
      if (this.stockSkuItemId != null) {
        events.add(new OrderRequestedJoinToStockAcceptedEvent(command.orderSkuItemId, command.orderId, command.skuId, command.stockSkuItemId, command.stockOrderId, Instant.now()));
      } else {
        events.add(new OrderRequestedJoinToStockEvent(command.orderSkuItemId, command.orderId, command.skuId));
        events.add(new OrderRequestedJoinToStockRejectedEvent(command.orderSkuItemId, command.orderId, command.skuId, command.stockSkuItemId, command.stockOrderId));
      }
      return events;
    }

    OrderRequestedJoinToStockEvent eventFor(OrderRequestedJoinToStockRejectedCommand command) {
      return new OrderRequestedJoinToStockEvent(command.orderSkuItemId, command.orderId, command.skuId);
    }

    Object eventFor(StockRequestsJoinToOrderCommand command) {
      if (stockSkuItemId == null || stockSkuItemId.equals(command.stockSkuItemId)) {
        return new StockRequestedJoinToOrderAcceptedEvent(command.orderSkuItemId, command.orderId, command.skuId, command.stockSkuItemId, command.stockOrderId);
      } else {
        return new StockRequestedJoinToOrderRejectedEvent(command.orderSkuItemId, command.orderId, command.skuId, command.stockSkuItemId, command.stockOrderId);
      }
    }

    StockRequestedJoinToOrderRejectedEvent eventsFor(StockRequestsJoinToOrderRejectedCommand command) {
      return new StockRequestedJoinToOrderRejectedEvent(command.orderSkuItemId, command.orderId, command.skuId, command.stockSkuItemId, command.stockOrderId);
    }

    BackOrderedSkuItemEvent eventFor(BackOrderSkuItemCommand command) {
      return new BackOrderedSkuItemEvent(command.orderSkuItemId, command.orderId, command.skuId, Instant.now());
    }

    State on(CreatedOrderSkuItemEvent event) {
      return new State(event.customerId, event.orderId, event.orderSkuItemId, event.skuId, event.skuName, null, null, event.orderedAt, null, null);
    }

    State on(OrderRequestedJoinToStockEvent event) {
      return this;
    }

    State on(OrderRequestedJoinToStockAcceptedEvent event) {
      return new State(customerId, orderId, orderSkuItemId, skuId, skuName, event.stockSkuItemId, event.stockOrderId, orderedAt, event.readyToShipAt, null);
    }

    State on(OrderRequestedJoinToStockRejectedEvent event) {
      return new State(customerId, orderId, orderSkuItemId, skuId, skuName, null, null, orderedAt, null, null);
    }

    State on(StockRequestedJoinToOrderAcceptedEvent event) {
      return new State(customerId, orderId, orderSkuItemId, skuId, skuName, event.stockSkuItemId, event.stockOrderId, orderedAt, Instant.now(), null);
    }

    State on(StockRequestedJoinToOrderRejectedEvent event) {
      return new State(customerId, orderId, orderSkuItemId, skuId, skuName, null, null, orderedAt, null, null);
    }

    State on(BackOrderedSkuItemEvent event) {
      return new State(customerId, orderId, orderSkuItemId, skuId, skuName, stockSkuItemId, stockOrderId, orderedAt, null, event.backOrderedAt);
    }
  }

  public record CreateOrderSkuItemCommand(String orderSkuItemId, String customerId, String orderId, String skuId, String skuName, Instant orderedAt) {}

  public record CreatedOrderSkuItemEvent(String orderSkuItemId, String customerId, String orderId, String skuId, String skuName, Instant orderedAt) {}

  public record OrderRequestedJoinToStockEvent(String orderSkuItemId, String orderId, String skuId) {}

  public record OrderRequestedJoinToStockAcceptedCommand(String orderSkuItemId, String orderId, String skuId, String stockSkuItemId, String stockOrderId) {}

  public record OrderRequestedJoinToStockAcceptedEvent(String orderSkuItemId, String orderId, String skuId, String stockSkuItemId, String stockOrderId, Instant readyToShipAt) {}

  public record OrderRequestedJoinToStockRejectedCommand(String orderSkuItemId, String orderId, String skuId, String stockSkuItemId, String stockOrderId) {}

  public record OrderRequestedJoinToStockRejectedEvent(String orderSkuItemId, String orderId, String skuId, String stockSkuItemId, String stockOrderId) {}

  public record StockRequestsJoinToOrderCommand(String orderSkuItemId, String orderId, String skuId, String stockSkuItemId, String stockOrderId) {}

  public record StockRequestedJoinToOrderAcceptedEvent(String orderSkuItemId, String orderId, String skuId, String stockSkuItemId, String stockOrderId) {}

  public record StockRequestsJoinToOrderRejectedCommand(String orderSkuItemId, String orderId, String skuId, String stockSkuItemId, String stockOrderId) {}

  public record StockRequestedJoinToOrderRejectedEvent(String orderSkuItemId, String orderId, String skuId, String stockSkuItemId, String stockOrderId) {}

  public record BackOrderSkuItemCommand(String orderSkuItemId, String orderId, String skuId) {}

  public record BackOrderedSkuItemEvent(String orderSkuItemId, String orderId, String skuId, Instant backOrderedAt) {}
}
