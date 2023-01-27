package io.example.shipping;

import java.time.Instant;
import java.util.List;

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

  @PutMapping("/order-requests-join-to-stock-accepted")
  public Effect<String> orderRequestedJoinToStockAccepted(@RequestBody OrderRequestsJoinToStockAcceptedCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/order-requests-join-to-stock-rejected")
  public Effect<String> orderRequestedJoinToStockRejected(@RequestBody OrderRequestsJoinToStockRejectedCommand command) {
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

  @PutMapping("/back-order-order-sku-item")
  public Effect<String> backOrderRequested(@RequestBody BackOrderSkuItemCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\nState: {}\nGetOrderSkuItem", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "OrderSkuItem not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
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
      String orderSkuItemId,
      String orderId,
      String skuId,
      String skuName,
      String customerId,
      StockSkuItemId stockSkuItemId,
      Instant orderedAt,
      Instant readyToShipAt,
      Instant backOrderedAt) {

    static State emptyState() {
      return new State(null, null, null, null, null, null, null, null, null);
    }

    boolean isEmpty() {
      return orderSkuItemId == null;
    }

    List<?> eventsFor(CreateOrderSkuItemCommand command) {
      return List.of(
          new CreatedOrderSkuItemEvent(
              command.orderSkuItemId,
              command.customerId,
              command.orderId,
              command.skuId,
              command.skuName,
              command.orderedAt),
          new OrderRequestedJoinToStockEvent(
              command.orderSkuItemId,
              command.orderId,
              command.skuId));
    }

    List<?> eventsFor(OrderRequestsJoinToStockAcceptedCommand command) {
      if (this.stockSkuItemId != null) {
        return List.of(
            new OrderRequestedJoinToStockAcceptedEvent(
                command.orderSkuItemId,
                command.orderId,
                command.skuId,
                command.stockSkuItemId,
                Instant.now()));
      } else {
        return List.of(
            new OrderRequestedJoinToStockEvent(
                command.orderSkuItemId,
                command.orderId,
                command.skuId),
            new OrderRequestedJoinToStockRejectedEvent(
                command.orderSkuItemId,
                command.orderId,
                command.skuId,
                command.stockSkuItemId));
      }
    }

    OrderRequestedJoinToStockEvent eventFor(OrderRequestsJoinToStockRejectedCommand command) {
      return new OrderRequestedJoinToStockEvent(
          command.orderSkuItemId,
          command.orderId,
          command.skuId);
    }

    Object eventFor(StockRequestsJoinToOrderCommand command) {
      if (stockSkuItemId == null || stockSkuItemId.equals(command.stockSkuItemId)) {
        return new StockRequestedJoinToOrderAcceptedEvent(
            command.orderSkuItemId,
            command.orderId,
            command.skuId,
            command.stockSkuItemId,
            Instant.now());
      } else {
        return new StockRequestedJoinToOrderRejectedEvent(
            command.orderSkuItemId,
            command.orderId,
            command.skuId,
            command.stockSkuItemId);
      }
    }

    StockRequestedJoinToOrderRejectedEvent eventsFor(StockRequestsJoinToOrderRejectedCommand command) {
      return new StockRequestedJoinToOrderRejectedEvent(
          command.orderSkuItemId,
          command.orderId,
          command.skuId,
          command.stockSkuItemId);
    }

    BackOrderedSkuItemEvent eventFor(BackOrderSkuItemCommand command) {
      return new BackOrderedSkuItemEvent(
          command.orderSkuItemId,
          command.orderId,
          command.skuId,
          Instant.now());
    }

    State on(CreatedOrderSkuItemEvent event) {
      return new State(
          event.orderSkuItemId,
          event.orderId,
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
          orderId,
          skuId,
          skuName,
          customerId,
          event.stockSkuItemId,
          orderedAt,
          event.readyToShipAt,
          null);
    }

    State on(OrderRequestedJoinToStockRejectedEvent event) {
      if (event.stockSkuItemId.equals(stockSkuItemId)) {
        return new State(
            orderSkuItemId,
            orderId,
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

    State on(StockRequestedJoinToOrderAcceptedEvent event) {
      return new State(
          orderSkuItemId,
          orderId,
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
            orderId,
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

    State on(BackOrderedSkuItemEvent event) {
      return new State(
          orderSkuItemId,
          orderId,
          skuId,
          skuName,
          customerId,
          null,
          orderedAt,
          null,
          event.backOrderedAt);
    }
  }

  public record CreateOrderSkuItemCommand(String orderSkuItemId, String customerId, String orderId, String skuId, String skuName, Instant orderedAt) {}

  public record CreatedOrderSkuItemEvent(String orderSkuItemId, String customerId, String orderId, String skuId, String skuName, Instant orderedAt) {}

  public record OrderRequestedJoinToStockEvent(String orderSkuItemId, String orderId, String skuId) {}

  public record OrderRequestsJoinToStockAcceptedCommand(String orderSkuItemId, String orderId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record OrderRequestedJoinToStockAcceptedEvent(String orderSkuItemId, String orderId, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) {}

  public record OrderRequestsJoinToStockRejectedCommand(String orderSkuItemId, String orderId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record OrderRequestedJoinToStockRejectedEvent(String orderSkuItemId, String orderId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record StockRequestsJoinToOrderCommand(String orderSkuItemId, String orderId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record StockRequestedJoinToOrderAcceptedEvent(String orderSkuItemId, String orderId, String skuId, StockSkuItemId stockSkuItemId, Instant readyToShipAt) {}

  public record StockRequestsJoinToOrderRejectedCommand(String orderSkuItemId, String orderId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record StockRequestedJoinToOrderRejectedEvent(String orderSkuItemId, String orderId, String skuId, StockSkuItemId stockSkuItemId) {}

  public record BackOrderSkuItemCommand(String orderSkuItemId, String orderId, String skuId) {}

  public record BackOrderedSkuItemEvent(String orderSkuItemId, String orderId, String skuId, Instant backOrderedAt) {}
}
