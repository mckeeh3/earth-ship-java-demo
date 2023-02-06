package io.example.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

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

@EntityKey("skuId")
@EntityType("product")
@RequestMapping("/product/{skuId}")
public class ProductEntity extends EventSourcedEntity<ProductEntity.State> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public ProductEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateProductCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return Validator.<Effect<String>>start()
        .isEmpty(command.skuId(), "Cannot create Product without skuId")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects()
            .emitEvent(currentState().eventFor(command))
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
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/update-units-back-ordered")
  public Effect<String> updateUnitsBackOrdered(@RequestBody UpdateProductUnitsBackOrderedCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
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
    return currentState().on(event);
  }

  @EventHandler
  public State on(AddedStockOrderEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedStockOrderEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedProductsBackOrderedEvent event) {
    return currentState().on(event);
  }

  public record State(
      String skuId,
      String skuName,
      String skuDescription,
      int available,
      int backOrdered,
      BigDecimal skuPrice,
      List<StockOrder> stockOrders,
      List<BackOrderedLot> backOrderedLots) {

    static State emptyState() {
      return new State(null, null, null, 0, 0, null, List.of(), List.of());
    }

    boolean isEmpty() {
      return skuId == null || skuId.isEmpty();
    }

    CreatedProductEvent eventFor(CreateProductCommand command) {
      return new CreatedProductEvent(command.skuId(), command.skuNAme(), command.skuDescription(), command.skuPrice());
    }

    AddedStockOrderEvent eventFor(AddStockOrderCommand command) {
      return new AddedStockOrderEvent(command.stockOrderId(), command.skuId(), command.quantityTotal());
    }

    UpdatedStockOrderEvent eventFor(UpdateStockOrderCommand command) {
      return new UpdatedStockOrderEvent(command.stockOrderId(), command.skuId(), command.quantityOrdered());
    }

    UpdatedProductsBackOrderedEvent eventFor(UpdateProductUnitsBackOrderedCommand command) {
      return new UpdatedProductsBackOrderedEvent(command.skuId(), command.backOrderedLot());
    }

    State on(CreatedProductEvent event) {
      return new State(
          event.skuId(),
          event.skuNAme(),
          event.skuDescription(),
          isEmpty() ? 0 : available,
          isEmpty() ? 0 : backOrdered,
          event.skuPrice(),
          stockOrders,
          backOrderedLots);
    }

    State on(AddedStockOrderEvent event) {
      var filteredStockOrders = stockOrders.stream()
          .filter(s -> !s.stockOrderId().equals(event.stockOrderId()));
      var addStockOrder = Stream.of(new StockOrder(event.stockOrderId(), event.quantityTotal(), 0, event.quantityTotal()));
      var newStockOrders = Stream.concat(filteredStockOrders, addStockOrder).toList();
      var quantityAvailable = newStockOrders.stream().mapToInt(StockOrder::quantityAvailable).sum();

      return new State(
          event.skuId(),
          skuName,
          skuDescription,
          quantityAvailable,
          backOrdered,
          skuPrice,
          newStockOrders,
          backOrderedLots);
    }

    State on(UpdatedStockOrderEvent event) {
      var newStockOrders = stockOrders.stream()
          .map(s -> s.stockOrderId().equals(event.stockOrderId())
              ? new StockOrder(s.stockOrderId(), s.quantityTotal(), event.quantityOrdered(), s.quantityTotal - event.quantityOrdered())
              : s)
          .filter(s -> s.quantityAvailable() > 0)
          .toList();
      var quantityAvailable = newStockOrders.stream().mapToInt(StockOrder::quantityAvailable).sum();

      return new State(
          event.skuId,
          skuName,
          skuDescription,
          quantityAvailable,
          backOrdered,
          skuPrice,
          newStockOrders,
          backOrderedLots);
    }

    State on(UpdatedProductsBackOrderedEvent event) {
      var filteredLots = backOrderedLots.stream()
          .filter(lot -> !lot.backOrderedLotId().equals(event.backOrderedLot().backOrderedLotId()));
      var addLot = event.backOrderedLot().quantityBackOrdered() > 0 ? Stream.of(event.backOrderedLot) : Stream.<BackOrderedLot>empty();
      var newBackOrderedLots = Stream.concat(filteredLots, addLot).toList();
      var quantityBackOrdered = newBackOrderedLots.stream().mapToInt(BackOrderedLot::quantityBackOrdered).sum();

      return new State(
          event.skuId,
          skuName,
          skuDescription,
          available,
          quantityBackOrdered,
          skuPrice,
          stockOrders,
          newBackOrderedLots);
    }
  }

  public record StockOrder(String stockOrderId, int quantityTotal, int quantityOrdered, int quantityAvailable) {}

  public record CreateProductCommand(String skuId, String skuNAme, String skuDescription, BigDecimal skuPrice) {}

  public record CreatedProductEvent(String skuId, String skuNAme, String skuDescription, BigDecimal skuPrice) {}

  public record AddStockOrderCommand(String stockOrderId, String skuId, int quantityTotal) {}

  public record AddedStockOrderEvent(String stockOrderId, String skuId, int quantityTotal) {}

  public record UpdateStockOrderCommand(String stockOrderId, String skuId, int quantityOrdered) {}

  public record UpdatedStockOrderEvent(String stockOrderId, String skuId, int quantityOrdered) {}

  public record UpdateProductUnitsBackOrderedCommand(String skuId, BackOrderedLot backOrderedLot) {}

  public record UpdatedProductsBackOrderedEvent(String skuId, BackOrderedLot backOrderedLot) {}
}
