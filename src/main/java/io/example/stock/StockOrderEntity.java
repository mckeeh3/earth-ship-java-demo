package io.example.stock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;

@Id("stockOrderId")
@TypeId("stockOrder")
@RequestMapping("/stock-order/{stockOrderId}")
public class StockOrderEntity extends EventSourcedEntity<StockOrderEntity.State, StockOrderEntity.Event> {
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

  @PutMapping("/create")
  public Effect<String> create(@RequestBody CreateStockOrderCommand command) {
    log.info("EntityID: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    if (currentState().stockOrderId() != null && !currentState().stockOrderId().isEmpty()) {
      return effects().reply("OK");
    }
    return Validator
        .isLtEqZero(command.quantityTotal(), "quantityTotal must be greater than 0")
        .isGtLimit(command.quantityTotal(), 1_000, "quantityTotal must be less than or equal to 1_000")
        .isEmpty(command.skuId, "SkuId must not be empty")
        .isEmpty(command.skuName, "SkuName must not be empty")
        .isEmpty(command.stockOrderId, "StockOrderId must not be empty")
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessages -> effects().error(errorMessages, Status.Code.INVALID_ARGUMENT));
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
    if (currentState().quantityCreated >= currentState().quantityTotal) {
      return effects().reply("OK");
    }
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityID: {}\n_State: {}\n_GetStockOrder", entityId, currentState());
    return Validator
        .isEmpty(currentState().stockOrderId(), "StockOrder does not exist")
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessages -> effects().error(errorMessages));
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
      int quantityTotal,
      int quantityCreated,
      int quantityOrdered,
      int quantityAvailable,
      Instant stockOrderReceivedAt) {

    static State emptyState() {
      return new State("", "", "", 0, 0, 0, 0, Instant.EPOCH);
    }

    boolean isEmpty() {
      return stockOrderId.isEmpty();
    }

    Event eventFor(CreateStockOrderCommand command) {
      return new CreatedStockOrderEvent(command.stockOrderId(), command.skuId(), command.skuName(), command.quantityTotal());
    }

    Event eventFor(UpdateStockOrderCommand command) {
      return new UpdatedStockOrderEvent(command.stockOrderId(), skuId, command.quantityOrdered());
    }

    Event eventFor(GenerateStockSkuItemIdsCommand command) {
      var limit = Math.min(quantityCreated + generateBatchSize, quantityTotal);
      var stockSkuItemIds = IntStream.range(quantityCreated, limit)
          .mapToObj(i -> StockSkuItemId.genId(command.stockOrderId(), skuId))
          .toList();
      return new GeneratedStockSkuItemIdsEvent(command.stockOrderId(), command.skuId(), quantityTotal, generateBatchSize, stockSkuItemIds);
    }

    State on(CreatedStockOrderEvent event) {
      return new State(
          event.stockOrderId(),
          event.skuId(),
          event.skuName(),
          event.quantityTotal(),
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
          quantityTotal,
          quantityCreated,
          event.quantityOrdered(),
          quantityTotal - event.quantityOrdered(),
          stockOrderReceivedAt);
    }

    State on(GeneratedStockSkuItemIdsEvent event) {
      return new State(
          stockOrderId,
          skuId,
          skuName,
          quantityTotal,
          quantityCreated + event.stockSkuItemIds().size(),
          quantityOrdered,
          quantityAvailable + event.stockSkuItemIds().size(),
          stockOrderReceivedAt);
    }
  }

  public static String genStockOrderId() {
    return UUID.randomUUID().toString();
  }

  public record StockSkuItemId(String stockOrderId, String skuId, UUID uuid) {
    static StockSkuItemId genId(String stockOrderId, String skuId) {
      return new StockSkuItemId(stockOrderId, skuId, UUID.randomUUID());
    }
  }

  public interface Event {}

  public record CreateStockOrderCommand(String stockOrderId, String skuId, String skuName, int quantityTotal) {}

  public record CreatedStockOrderEvent(String stockOrderId, String skuId, String skuName, int quantityTotal) implements Event {}

  public record UpdateStockOrderCommand(String stockOrderId, int quantityOrdered) {}

  public record UpdatedStockOrderEvent(String stockOrderId, String skuId, int quantityOrdered) implements Event {}

  public record GenerateStockSkuItemIdsCommand(String stockOrderId, String skuId) {}

  public record GeneratedStockSkuItemIdsEvent(String stockOrderId, String skuId, int quantityTotal, int quantityPerBatch, List<StockSkuItemId> stockSkuItemIds) implements Event {}

  public record GenerateStockSkuItem(String stockOrder, StockSkuItemId stockSkuItemId, String skuId, String skuName) {}
}
