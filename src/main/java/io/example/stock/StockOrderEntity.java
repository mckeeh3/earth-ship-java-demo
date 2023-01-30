package io.example.stock;

import java.time.Instant;
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
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("stockOrderId")
@EntityType("StockOrder")
@RequestMapping("/stockOrder/{stockOrderId}")
public class StockOrderEntity extends EventSourcedEntity<StockOrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(StockOrderEntity.class);
  private final String entityId;
  private static final int generateBatchSize = 32;

  public StockOrderEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateStockOrderCommand command) {
    log.info("EntityID: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isNotEmpty(currentState().stockOrderId(), "StockOrder already exists")
        .isLtEqZero(command.orderItemsTotal(), "OrderItemsTotal must be greater than 0")
        .isGtLimit(generateBatchSize, 1_000, "OrderItemsTotal must be less than or equal to 1_000")
        .isEmpty(command.skuId, "SkuId must not be empty")
        .isEmpty(command.skuName, "SkuName must not be empty")
        .isEmpty(command.stockOrderId, "StockOrderId must not be empty")
        .onError(errorMessages -> effects().error(errorMessages, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/update")
  public Effect<String> update(@RequestBody UpdateStockOrderCommand command) {
    log.info("EntityID: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/generate-stock-sku-item-ids")
  public Effect<String> generateStockSkuItemIds(@RequestBody GenerateStockSkuItemIdsCommand command) {
    log.info("EntityID: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityID: {}\n_State: {}\n_GetStockOrder", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isEmpty(currentState().stockOrderId(), "StockOrder does not exist")
        .onError(errorMessages -> effects().error(errorMessages))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(CreatedStockOrderEvent event) {
    log.info("EntityID: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedStockOrderEvent event) {
    log.info("EntityID: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(GeneratedStockSkuItemIdsEvent event) {
    log.info("EntityID: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String stockOrderId,
      String skuId,
      String skuName,
      int orderItemsTotal,
      int orderItemsCreated,
      int orderItemsOrdered,
      int orderItemsAvailable,
      Instant orderReceivedAt) {

    static State emptyState() {
      return new State("", "", "", 0, 0, 0, 0, Instant.EPOCH);
    }

    boolean isEmpty() {
      return stockOrderId.isEmpty();
    }

    CreatedStockOrderEvent eventFor(CreateStockOrderCommand command) {
      return new CreatedStockOrderEvent(command.stockOrderId(), command.skuId(), command.skuName(), command.orderItemsTotal());
    }

    UpdatedStockOrderEvent eventFor(UpdateStockOrderCommand command) {
      return new UpdatedStockOrderEvent(command.stockOrderId(), command.orderItemsOrdered(), command.orderItemsAvailable());
    }

    GeneratedStockSkuItemIdsEvent eventFor(GenerateStockSkuItemIdsCommand command) {
      var limit = Math.min(orderItemsCreated + generateBatchSize, orderItemsTotal);
      var generateCommands = IntStream.range(orderItemsCreated, limit)
          .mapToObj(i -> {
            var stockSkuItemId = StockSkuItemId.of(stockOrderId, orderItemsTotal, i);
            return new GenerateStockSkuItem(stockSkuItemId, skuId, skuName, stockOrderId);
          })
          .toList();
      return new GeneratedStockSkuItemIdsEvent(command.stockOrderId(), generateCommands);
    }

    State on(CreatedStockOrderEvent event) {
      return new State(
          event.stockOrderId(),
          event.skuId(),
          event.skuName(),
          event.orderItemsTotal(),
          0,
          0,
          0,
          Instant.now());
    }

    State on(UpdatedStockOrderEvent event) {
      return new State(
          stockOrderId,
          skuId,
          skuName,
          orderItemsTotal,
          orderItemsCreated,
          event.orderItemsOrdered(),
          event.orderItemsAvailable(),
          orderReceivedAt);
    }

    State on(GeneratedStockSkuItemIdsEvent event) {
      return new State(
          stockOrderId,
          skuId,
          skuName,
          orderItemsTotal,
          orderItemsCreated + event.generateStockSkuItems().size(),
          orderItemsOrdered,
          orderItemsAvailable,
          orderReceivedAt);
    }
  }

  public record CreateStockOrderCommand(String stockOrderId, String skuId, String skuName, int orderItemsTotal) {}

  public record CreatedStockOrderEvent(String stockOrderId, String skuId, String skuName, int orderItemsTotal) {}

  public record UpdateStockOrderCommand(String stockOrderId, int orderItemsOrdered, int orderItemsAvailable) {}

  public record UpdatedStockOrderEvent(String stockOrderId, int orderItemsOrdered, int orderItemsAvailable) {}

  public record GenerateStockSkuItemIdsCommand(String stockOrderId) {}

  public record GeneratedStockSkuItemIdsEvent(String stockOrderId, List<GenerateStockSkuItem> generateStockSkuItems) {}

  public record GenerateStockSkuItem(StockSkuItemId stockSkuItemId, String skuId, String skuName, String stockOrderId) {}
}
