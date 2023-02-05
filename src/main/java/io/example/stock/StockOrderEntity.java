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
@EntityType("stockOrder")
@RequestMapping("/stock-order/{stockOrderId}")
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
        .isLtEqZero(command.quantityTotal(), "quantityTotal must be greater than 0")
        .isGtLimit(command.quantityTotal(), 1_000, "quantityTotal must be less than or equal to 1_000")
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

    CreatedStockOrderEvent eventFor(CreateStockOrderCommand command) {
      return new CreatedStockOrderEvent(command.stockOrderId(), command.skuId(), command.skuName(), command.quantityTotal());
    }

    UpdatedStockOrderEvent eventFor(UpdateStockOrderCommand command) {
      return new UpdatedStockOrderEvent(command.stockOrderId(), command.quantityTotal(), command.quantityOrdered());
    }

    GeneratedStockSkuItemIdsEvent eventFor(GenerateStockSkuItemIdsCommand command) {
      var limit = Math.min(quantityCreated + generateBatchSize, quantityTotal);
      var generateCommands = IntStream.range(quantityCreated, limit)
          .mapToObj(i -> {
            var stockSkuItemId = StockSkuItemId.of(stockOrderId, quantityTotal, i);
            return new GenerateStockSkuItem(stockSkuItemId, skuId, skuName);
          })
          .toList();
      return new GeneratedStockSkuItemIdsEvent(command.stockOrderId(), generateCommands);
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
          quantityCreated + event.generateStockSkuItems().size(),
          quantityOrdered,
          quantityAvailable + event.generateStockSkuItems().size(),
          stockOrderReceivedAt);
    }
  }

  public record CreateStockOrderCommand(String stockOrderId, String skuId, String skuName, int quantityTotal) {}

  public record CreatedStockOrderEvent(String stockOrderId, String skuId, String skuName, int quantityTotal) {}

  public record UpdateStockOrderCommand(String stockOrderId, int quantityTotal, int quantityOrdered) {}

  public record UpdatedStockOrderEvent(String stockOrderId, int quantityTotal, int quantityOrdered) {}

  public record GenerateStockSkuItemIdsCommand(String stockOrderId) {}

  public record GeneratedStockSkuItemIdsEvent(String stockOrderId, List<GenerateStockSkuItem> generateStockSkuItems) {}

  public record GenerateStockSkuItem(StockSkuItemId stockSkuItemId, String skuId, String skuName) {}
}
