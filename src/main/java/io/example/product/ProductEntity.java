package io.example.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    return Validator.<Effect<String>>start()
        .isEmpty(command.skuId(), "Cannot create Product without skuId")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvents(currentState().eventsFor(command))
            .thenReply(__ -> "OK"));
  }

  @PutMapping("/add-stock-order")
  public Effect<String> addStockOrder(@RequestBody AddStockOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/update-stock-order")
  public Effect<String> updateStockOrder(@RequestBody UpdateStockOrderCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/update-units-back-ordered")
  public Effect<String> updateUnitsBackOrdered(@RequestBody UpdateProductsBackOrderedCommandOLD command) {
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
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "Product not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
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
  public State on(UpdatedProductsBackOrderedEventOLD event) {
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
      List<StockOrder> stockOrders,
      List<BackOrderedLot> backOrderedLotsOLD) {

    static State emptyState() {
      return new State(null, null, null, 0, 0, null, List.of(), List.of());
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

    Event eventFor(AddStockOrderCommand command) {
      return new AddedStockOrderEvent(command.stockOrderId(), command.skuId(), command.quantityTotal());
    }

    List<Event> eventsFor(UpdateStockOrderCommand command) {
      var event = new UpdatedStockOrderEvent(command.stockOrderId(), command.skuId(), command.quantityOrdered());
      var newState = on(event);
      if (newState.quantityAvailable < quantityAvailableLowThreshold) {
        var stockOrderId = generateStockOrderId(skuId);
        return List.of(
            event,
            new AddedStockOrderEvent(stockOrderId, skuId, quantityPerStockOrder),
            new CreateStockOrderRequestedEvent(stockOrderId, skuId, skuName, quantityPerStockOrder));
      }
      return List.of(event);
    }

    List<Event> eventsFor(UpdateProductsBackOrderedCommandOLD command) {
      var event = new UpdatedProductsBackOrderedEventOLD(command.skuId(), command.backOrderedLot());
      var newState = on(event);

      if (newState.quantityBackOrdered > newState.quantityAvailable) {
        var stockOrderId = generateStockOrderId(skuId);
        return List.of(
            event,
            new AddedStockOrderEvent(stockOrderId, skuId, quantityPerStockOrder),
            new CreateStockOrderRequestedEvent(stockOrderId, skuId, skuName, quantityPerStockOrder));
      }
      return List.of(event);
    }

    List<Event> eventsFor(UpdateBackOrderedCommand command) {
      var event = new UpdatedBackOrderedEvent(command.skuId(), command.quantityBackOrdered());
      var newState = on(event);

      if (newState.quantityBackOrdered > newState.quantityAvailable) {
        var stockOrderId = generateStockOrderId(skuId);
        return List.of(
            event,
            new AddedStockOrderEvent(stockOrderId, skuId, quantityPerStockOrder),
            new CreateStockOrderRequestedEvent(stockOrderId, skuId, skuName, quantityPerStockOrder));
      }
      return List.of(event);
    }

    State on(CreatedProductEvent event) {
      return new State(
          event.skuId(),
          event.skuName(),
          event.skuDescription(),
          isEmpty() ? 0 : quantityAvailable,
          isEmpty() ? 0 : quantityBackOrdered,
          event.skuPrice(),
          stockOrders,
          backOrderedLotsOLD);
    }

    State on(AddedStockOrderEvent event) {
      if (stockOrders.stream().anyMatch(s -> s.stockOrderId().equals(event.stockOrderId()))) {
        return this;
      }
      var addStockOrder = Stream.of(new StockOrder(event.stockOrderId(), event.quantityTotal(), 0, event.quantityTotal()));
      var newStockOrders = Stream.concat(stockOrders.stream(), addStockOrder).toList();
      var newQuantityAvailable = newStockOrders.stream().mapToInt(StockOrder::quantityAvailable).sum();

      return new State(
          skuId,
          skuName,
          skuDescription,
          newQuantityAvailable,
          quantityBackOrdered,
          skuPrice,
          newStockOrders,
          backOrderedLotsOLD);
    }

    State on(UpdatedStockOrderEvent event) {
      var newStockOrders = stockOrders.stream()
          .map(s -> s.stockOrderId().equals(event.stockOrderId())
              ? new StockOrder(s.stockOrderId(), s.quantityTotal(), event.quantityOrdered(), s.quantityTotal - event.quantityOrdered())
              : s)
          .filter(s -> s.quantityAvailable() > 0)
          .toList();
      var newQuantityAvailable = newStockOrders.stream().mapToInt(StockOrder::quantityAvailable).sum();

      return new State(
          skuId,
          skuName,
          skuDescription,
          newQuantityAvailable,
          quantityBackOrdered,
          skuPrice,
          newStockOrders,
          backOrderedLotsOLD);
    }

    State on(UpdatedProductsBackOrderedEventOLD event) {
      var filteredLots = backOrderedLotsOLD.stream()
          .filter(lot -> !lot.backOrderedLotId().equals(event.backOrderedLot().backOrderedLotId()));
      var addLot = event.backOrderedLot().quantityBackOrdered() > 0 ? Stream.of(event.backOrderedLot) : Stream.<BackOrderedLot>empty();
      var newBackOrderedLots = Stream.concat(filteredLots, addLot).toList();
      var quantityBackOrdered = newBackOrderedLots.stream().mapToInt(BackOrderedLot::quantityBackOrdered).sum();

      return new State(
          event.skuId,
          skuName,
          skuDescription,
          quantityAvailable,
          quantityBackOrdered,
          skuPrice,
          stockOrders,
          newBackOrderedLots);
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
          stockOrders,
          backOrderedLotsOLD);
    }

    String generateStockOrderId(String skuId) {
      return "%s-%s".formatted(skuId(), UUID.randomUUID().toString());
    }
  }

  public record StockOrder(String stockOrderId, int quantityTotal, int quantityOrdered, int quantityAvailable) {}

  public interface Event {}

  public record CreateProductCommand(String skuId, String skuName, String skuDescription, BigDecimal skuPrice) {}

  public record CreatedProductEvent(String skuId, String skuName, String skuDescription, BigDecimal skuPrice) implements Event {}

  public record AddStockOrderCommand(String stockOrderId, String skuId, int quantityTotal) {}

  public record AddedStockOrderEvent(String stockOrderId, String skuId, int quantityTotal) implements Event {}

  public record UpdateStockOrderCommand(String stockOrderId, String skuId, int quantityOrdered) {}

  public record UpdatedStockOrderEvent(String stockOrderId, String skuId, int quantityOrdered) implements Event {}

  public record UpdateProductsBackOrderedCommandOLD(String skuId, BackOrderedLot backOrderedLot) {}

  public record UpdatedProductsBackOrderedEventOLD(String skuId, BackOrderedLot backOrderedLot) implements Event {}

  public record CreateStockOrderRequestedEvent(String stockOrderId, String skuId, String skuName, int quantityTotal) implements Event {}

  public record UpdateBackOrderedCommand(String skuId, int quantityBackOrdered) {}

  public record UpdatedBackOrderedEvent(String skuId, int quantityBackOrdered) implements Event {}
}
