package io.example.stock;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("stockOrderLotId")
@EntityType("stock-order-lot")
@RequestMapping("/stockOrderLot/{stockOrderLotId}")
public class StockOrderLotEntity extends EventSourcedEntity<StockOrderLotEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(StockOrderLotEntity.class);
  private final String entityId;

  public StockOrderLotEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/updateSubStockOrderLot")
  public Effect<String> updateSubStockOrderLot(@RequestBody UpdateSubStockOrderLotCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/releaseStockOrderLot")
  public Effect<String> releaseStockOrderLot(@RequestBody ReleaseStockOrderLotCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isNull(currentState().stockOrderLot(), "StockOrderLot not found")
        .onError(errorMessage -> effects().error(errorMessage))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(UpdatedStockOrderLotEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedSubStockOrderLotEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedStockOrderLotEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      StockOrderLot stockOrderLot,
      boolean hasChanged) {

    static State emptyState() {
      return new State(null, false);
    }

    boolean isEmpty() {
      return stockOrderLot == null;
    }

    List<?> eventsFor(UpdateSubStockOrderLotCommand command) {
      if (hasChanged) {
        return List.of(new UpdatedSubStockOrderLotEvent(command.subStockOrderLotId, command.subStockOrderLot));
      } else {
        var upperStockOrderLotId = command.subStockOrderLotId.levelUp();
        return List.of(
            new UpdatedSubStockOrderLotEvent(command.subStockOrderLotId, command.subStockOrderLot),
            new UpdatedStockOrderLotEvent(upperStockOrderLotId));
      }
    }

    ReleasedStockOrderLotEvent eventFor(ReleaseStockOrderLotCommand command) {
      return new ReleasedStockOrderLotEvent(stockOrderLot.stockOrderLotId(), stockOrderLot.copyWithoutSubLots());
    }

    State on(UpdatedStockOrderLotEvent event) {
      return this;
    }

    State on(UpdatedSubStockOrderLotEvent event) {
      if (isEmpty()) {
        var newStockOrderLot = new StockOrderLot(
            event.subStockOrderLotId().levelUp(),
            event.subStockOrderLot.quantityTotal(),
            event.subStockOrderLot.quantityOrdered(),
            List.of(event.subStockOrderLot));
        return new State(newStockOrderLot, true);
      }

      var filteredLots = stockOrderLot.subStockOrderLots().stream()
          .filter(s -> !s.stockOrderLotId().equals(event.subStockOrderLotId));
      var addLot = Stream.of(event.subStockOrderLot);
      var newSubStockOrderLots = Stream.concat(filteredLots, addLot).toList();

      var zeroStockOrderLot = new StockOrderLot(event.subStockOrderLotId().levelUp(), 0, 0, newSubStockOrderLots);
      var newStockOrderLot = newSubStockOrderLots.stream()
          .reduce(zeroStockOrderLot, (a, s) -> new StockOrderLot(
              a.stockOrderLotId(),
              a.quantityTotal() + s.quantityTotal(),
              a.quantityOrdered() + s.quantityOrdered(),
              a.subStockOrderLots()));

      return new State(newStockOrderLot, true);
    }

    State on(ReleasedStockOrderLotEvent event) {
      return new State(stockOrderLot, false);
    }
  }

  public record UpdateSubStockOrderLotCommand(StockOrderLotId subStockOrderLotId, StockOrderLot subStockOrderLot) {}

  public record UpdatedSubStockOrderLotEvent(StockOrderLotId subStockOrderLotId, StockOrderLot subStockOrderLot) {}

  public record UpdatedStockOrderLotEvent(StockOrderLotId stockOrderLotId) {}

  public record ReleaseStockOrderLotCommand(StockOrderLotId stockOrderLotId) {}

  public record ReleasedStockOrderLotEvent(StockOrderLotId stockOrderLotId, StockOrderLot stockOrderLot) {}
}
