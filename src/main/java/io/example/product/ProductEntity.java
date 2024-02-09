package io.example.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.stock.StockOrderEntity;
import io.grpc.Status;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("skuId")
@TypeId("product")
@RequestMapping("/product/{skuId}")
public class ProductEntity extends EventSourcedEntity<ProductEntity.State, ProductEntity.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;
  static final int quantityPerStockOrder = 100;
  static final int quantityAvailableLowThreshold = quantityPerStockOrder / 2;

  public ProductEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/create")
  public Effect<String> create(@RequestBody CreateProductCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator
        .isEmpty(command.skuId(), "Cannot create Product without skuId")
        .onSuccess(() -> effects()
            .emitEvents(currentState().eventsFor(command))
            .thenReply(__ -> "OK"))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT));
  }

  @PutMapping("/add-stock-order")
  public Effect<String> addStockOrder(@RequestBody AddStockOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/update-stock-order")
  public Effect<String> updateStockOrder(@RequestBody UpdateStockOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/update-back-ordered")
  public Effect<String> updateBackOrdered(@RequestBody UpdateBackOrderedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetProduct", entityId, currentState());
    return Validator
        .isTrue(currentState().isEmpty(), "Product not found")
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND));
  }

  @EventHandler
  public State on(CreatedProductEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(AddedStockOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedStockOrderEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(CreateStockOrderRequestedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedBackOrderedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String skuId,
      String skuName,
      String skuDescription,
      int quantityAvailable,
      int quantityBackOrdered,
      BigDecimal skuPrice,
      List<StockOrder> stockOrders) {

    static State emptyState() {
      return new State(null, null, null, 0, 0, null, List.of());
    }

    boolean isEmpty() {
      return skuId == null || skuId.isEmpty();
    }

    List<Event> eventsFor(CreateProductCommand command) {
      if (!isEmpty()) {
        return List.of();
      }
      return List.of(new CreatedProductEvent(command.skuId(), command.skuName(), command.skuDescription(), command.skuPrice()));
    }

    List<Event> eventsFor(AddStockOrderCommand command) {
      if (stockOrders.stream().anyMatch(s -> s.stockOrderId().equals(command.stockOrderId()))) {
        return List.of();
      }

      var newStockOrder = new StockOrder(command.stockOrderId(), command.quantityTotal(), 0, command.quantityTotal());
      var newStockOrders = Stream.concat(stockOrders.stream(), Stream.of(newStockOrder)).toList();
      var newQuantityAvailable = newStockOrders.stream().mapToInt(StockOrder::quantityAvailable).sum();

      return List.of(new AddedStockOrderEvent(command.stockOrderId(), command.skuId(), command.quantityTotal(), newQuantityAvailable, newStockOrders));
    }

    List<Event> eventsFor(UpdateStockOrderCommand command) {
      var newStockOrders = stockOrders.stream()
          .map(s -> s.stockOrderId().equals(command.stockOrderId())
              ? new StockOrder(s.stockOrderId(), s.quantityTotal(), command.quantityOrdered(), s.quantityTotal - command.quantityOrdered())
              : s)
          .filter(s -> s.quantityAvailable() > 0)
          .toList();
      var newQuantityAvailable = newStockOrders.stream().mapToInt(StockOrder::quantityAvailable).sum();
      var stockOrderId = StockOrderEntity.genStockOrderId();

      var event = new UpdatedStockOrderEvent(command.stockOrderId(), command.skuId(), command.quantityOrdered(), newQuantityAvailable, newStockOrders);

      return newQuantityAvailable < quantityAvailableLowThreshold
          ? List.of(
              event,
              new CreateStockOrderRequestedEvent(stockOrderId, skuId, skuName, quantityPerStockOrder))
          : List.of(event);
    }

    List<Event> eventsFor(UpdateBackOrderedCommand command) {
      var event = new UpdatedBackOrderedEvent(command.skuId(), command.quantityBackOrdered());

      var stockOrderId = StockOrderEntity.genStockOrderId();

      return event.quantityBackOrdered > 0 && quantityBackOrdered == 0
          ? List.of(
              event,
              new CreateStockOrderRequestedEvent(stockOrderId, skuId, skuName, quantityPerStockOrder))
          : List.of(event);
    }

    State on(CreatedProductEvent event) {
      return new State(
          event.skuId(),
          event.skuName(),
          event.skuDescription(),
          0,
          0,
          event.skuPrice(),
          List.of());
    }

    State on(AddedStockOrderEvent event) {
      return new State(
          skuId,
          skuName,
          skuDescription,
          event.quantityAvailable,
          quantityBackOrdered,
          skuPrice,
          event.stockOrders);
    }

    State on(UpdatedStockOrderEvent event) {
      return new State(
          skuId,
          skuName,
          skuDescription,
          event.quantityAvailable,
          quantityBackOrdered,
          skuPrice,
          event.stockOrders);
    }

    State on(CreateStockOrderRequestedEvent event) {
      return this;
    }

    State on(UpdatedBackOrderedEvent event) {
      return new State(
          skuId,
          skuName,
          skuDescription,
          quantityAvailable,
          event.quantityBackOrdered,
          skuPrice,
          stockOrders);
    }
  }

  public record StockOrder(String stockOrderId, int quantityTotal, int quantityOrdered, int quantityAvailable) {}

  public interface Event {}

  public record CreateProductCommand(String skuId, String skuName, String skuDescription, BigDecimal skuPrice) {}

  public record CreatedProductEvent(String skuId, String skuName, String skuDescription, BigDecimal skuPrice) implements Event {}

  public record AddStockOrderCommand(String stockOrderId, String skuId, int quantityTotal) {}

  public record AddedStockOrderEvent(String stockOrderId, String skuId, int quantityTotal, int quantityAvailable, List<StockOrder> stockOrders) implements Event {}

  public record UpdateStockOrderCommand(String stockOrderId, String skuId, int quantityOrdered) {}

  public record UpdatedStockOrderEvent(String stockOrderId, String skuId, int quantityOrdered, int quantityAvailable, List<StockOrder> stockOrders) implements Event {}

  public record CreateStockOrderRequestedEvent(String stockOrderId, String skuId, String skuName, int quantityTotal) implements Event {}

  public record UpdateBackOrderedCommand(String skuId, int quantityBackOrdered) {}

  public record UpdatedBackOrderedEvent(String skuId, int quantityBackOrdered) implements Event {}
}
