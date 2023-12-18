package io.example.stock;

import java.time.Instant;
import java.util.List;

import io.example.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.shipping.OrderSkuItemEntity.OrderSkuItemId;
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;


@Id("stockSkuItemEntityId")
@TypeId("stockSkuItem")
@RequestMapping("/stock-sku-item/{stockSkuItemEntityId}")
public class StockSkuItemEntity extends EventSourcedEntity<StockSkuItemEntity.State, StockSkuItemEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemEntity.class);
  private final String entityId;

  public StockSkuItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/create")
  public Effect<String> create(@RequestBody CreateStockSkuItemCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    if (currentState().isEmpty()) {
      return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
    }
    return effects().reply("OK");
  }

  @PutMapping("/activate")
  public Effect<String> activate(@RequestBody StockSkuItemActivateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    if (currentState().createdAt() == null) {
      return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
    }
    return effects().reply("OK");
  }

  @PutMapping("/reserve-stock-sku-item")
  public Effect<Result> reserve(@RequestBody ReserveStockSkuItemCommand cmd) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), cmd);
    if (currentState().orderSkuItemId == null) {
      return effects()
        .emitEvent(currentState().eventFor(cmd))
        .thenReply(__ -> Result.successful());
    } else if (currentState().orderSkuItemId.equals(cmd.orderSkuItemId)) {
      log.info("StockSkuItem '%s' already reserved by order '%s'".formatted(entityId, currentState().orderSkuItemId));
      return effects().reply(Result.successful());
    } else {
      // already reserved by other order
      return effects().reply(Result.failure("StockSkuItem '%s' already reserved by order '%s'"
        .formatted(entityId, currentState().orderSkuItemId)));

    }
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
    log.info("EntityId: {}\n_State: {}\n_GetStockSkuItem", entityId, currentState());
    return Validator.<Effect<State>>start()
      .isTrue(currentState().isEmpty(), "StockSkuItem '%s' not found".formatted(entityId))
      .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
      .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedStockSkuItemEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockSkuItemActivatedEvent event) {
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
    Instant readyToShipAt,
    Instant createdAt) {

    static State emptyState() {
      return new State(null, null, null, null, null, null);
    }

    boolean isEmpty() {
      return stockSkuItemId == null;
    }

    List<? extends Event> eventsFor(CreateStockSkuItemCommand command) {
      if (!isEmpty()) {
        return List.of(); // already created
      }

      return List.of(
        new CreatedStockSkuItemEvent(
          command.stockSkuItemId,
          command.skuId,
          command.skuName),
        new StockRequestedJoinToOrderEvent(
          command.stockSkuItemId,
          command.skuId));
    }

    Event eventFor(StockSkuItemActivateCommand command) {
      return new StockSkuItemActivatedEvent(
        command.stockSkuItemId,
        command.skuId);
    }

    Event eventFor(OrderRequestsJoinToStockCommand command) {
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

    List<? extends Event> eventsFor(StockRequestsJoinToOrderAcceptedCommand command) {
      if (command.orderSkuItemId.equals(orderSkuItemId) && command.readyToShipAt.equals(readyToShipAt)) {
        return List.of(); // already accepted
      }

      if (orderSkuItemId == null) {
        return List.of((new StockRequestedJoinToOrderAcceptedEvent(
          command.stockSkuItemId,
          command.skuId,
          command.orderSkuItemId,
          command.readyToShipAt)));
      }

      return List.of(
        new StockRequestedJoinToOrderEvent(
          command.stockSkuItemId,
          command.skuId),
        new StockRequestedJoinToOrderReleasedEvent(
          command.stockSkuItemId,
          command.skuId,
          command.orderSkuItemId));
    }

    Event eventFor(StockRequestsJoinToOrderRejectedCommand command) {
      return new StockRequestedJoinToOrderEvent(
        command.stockSkuItemId,
        command.skuId);
    }

    Event eventFor(OrderRequestsJoinToStockReleasedCommand command) {
      return new OrderRequestedJoinToStockReleasedEvent(
        command.stockSkuItemId,
        command.skuId,
        command.orderSkuItemId);
    }

    State on(CreatedStockSkuItemEvent event) {
      if (!isEmpty()) {
        return this;
      }
      return new State(
        event.stockSkuItemId,
        event.skuId,
        event.skuName,
        null,
        null,
        null);
    }

    State on(StockSkuItemActivatedEvent event) {
      if (createdAt != null) {
        return this;
      }
      return new State(
        stockSkuItemId,
        skuId,
        skuName,
        null,
        null,
        Instant.now());
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
        event.readyToShipAt,
        createdAt);
    }

    State on(OrderRequestedJoinToStockRejectedEvent event) {
      if (event.orderSkuItemId.equals(orderSkuItemId)) {
        return new State(
          stockSkuItemId,
          skuId,
          skuName,
          null,
          null,
          createdAt);
      }
      return this;
    }

    State on(StockRequestedJoinToOrderAcceptedEvent event) {
      return new State(
        stockSkuItemId,
        skuId,
        skuName,
        event.orderSkuItemId,
        event.readyToShipAt,
        Instant.now());
    }

    State on(OrderRequestedJoinToStockReleasedEvent event) {
      if (event.orderSkuItemId.equals(orderSkuItemId)) {
        return new State(
          stockSkuItemId,
          skuId,
          skuName,
          null,
          null,
          createdAt);
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
          null,
          createdAt);
      }
      return this;
    }

    public Event eventFor(ReserveStockSkuItemCommand cmd) {
      return new StockSkuItemReservedEvent(
        cmd.stockSkuItemId,
        cmd.skuId,
        cmd.orderSkuItemId,
        Instant.now());
    }
  }

  public interface Event {
  }

  public record CreateStockSkuItemCommand(StockSkuItemId stockSkuItemId, String skuId, String skuName) {
  }

  public record CreatedStockSkuItemEvent(StockSkuItemId stockSkuItemId, String skuId, String skuName) implements Event {
  }

  public record StockSkuItemActivateCommand(StockSkuItemId stockSkuItemId, String skuId) {
  }

  public record StockSkuItemActivatedEvent(StockSkuItemId stockSkuItemId, String skuId) implements Event {
  }

  public record StockRequestedJoinToOrderEvent(StockSkuItemId stockSkuItemId, String skuId) implements Event {
  }

  public record ReserveStockSkuItemCommand(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId) {
  }

  public record OrderRequestsJoinToStockCommand(StockSkuItemId stockSkuItemId, String skuId,
                                                OrderSkuItemId orderSkuItemId) {
  }

  public record StockSkuItemReservedEvent(StockSkuItemId stockSkuItemId, String skuId, OrderSkuItemId orderSkuItemId,
                                          Instant readyToShipAt) implements Event {
  }

  public record OrderRequestedJoinToStockAcceptedEvent(StockSkuItemId stockSkuItemId, String skuId,
                                                       OrderSkuItemId orderSkuItemId,
                                                       Instant readyToShipAt) implements Event {
  }

  public record OrderRequestedJoinToStockRejectedEvent(StockSkuItemId stockSkuItemId, String skuId,
                                                       OrderSkuItemId orderSkuItemId) implements Event {
  }

  public record StockRequestsJoinToOrderAcceptedCommand(StockSkuItemId stockSkuItemId, String skuId,
                                                        OrderSkuItemId orderSkuItemId, Instant readyToShipAt) {
  }

  public record StockRequestedJoinToOrderAcceptedEvent(StockSkuItemId stockSkuItemId, String skuId,
                                                       OrderSkuItemId orderSkuItemId,
                                                       Instant readyToShipAt) implements Event {
  }

  public record StockRequestsJoinToOrderRejectedCommand(StockSkuItemId stockSkuItemId, String skuId,
                                                        OrderSkuItemId orderSkuItemId) {
  }

  public record OrderRequestsJoinToStockReleasedCommand(StockSkuItemId stockSkuItemId, String skuId,
                                                        OrderSkuItemId orderSkuItemId) {
  }

  public record OrderRequestedJoinToStockReleasedEvent(StockSkuItemId stockSkuItemId, String skuId,
                                                       OrderSkuItemId orderSkuItemId) implements Event {
  }

  public record StockRequestedJoinToOrderReleasedEvent(StockSkuItemId stockSkuItemId, String skuId,
                                                       OrderSkuItemId orderSkuItemId) implements Event {
  }

  public record StockSkuItemId(StockOrderLotId stockOrderLotId, int stockSkuItemNumber) {
    public String toEntityId() {
      return "%s_%d".formatted(stockOrderLotId.toEntityId(), stockSkuItemNumber);
    }

    private static int lotLevelsFor(int stockOrderItemsTotal) {
      return (int) Math.ceil(Math.log(stockOrderItemsTotal) / Math.log(StockOrderLotId.subLotsPerLot));
    }

    static StockSkuItemId of(String stockOrderId, int stockOrderItemsTotal, int stockOrderItemNumber) {
      var lotLevel = lotLevelsFor(stockOrderItemsTotal);
      var lotNumber = stockOrderItemNumber;
      var stockOrderLotId = new StockOrderLotId(stockOrderId, lotLevel, lotNumber);
      return new StockSkuItemId(stockOrderLotId, stockOrderItemNumber);
    }

    StockOrderLotId levelUp() {
      return stockOrderLotId.levelUp();
    }
  }
}
