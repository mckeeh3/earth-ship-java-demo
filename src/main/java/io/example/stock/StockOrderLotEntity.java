package io.example.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.EntityKey;
import kalix.javasdk.annotations.EntityType;
import kalix.javasdk.annotations.EventHandler;

@EntityKey("stockOrderLotId")
@EntityType("stockOrderLot")
@RequestMapping("/stock-order-lot/{stockOrderLotId}")
public class StockOrderLotEntity extends EventSourcedEntity<StockOrderLotEntity.State, StockOrderLotEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(StockOrderLotEntity.class);
  private final String entityId;

  public StockOrderLotEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/update")
  public Effect<String> updateSubStockOrderLot(@RequestBody UpdateSubStockOrderLotCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/release")
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

    List<? extends Event> eventsFor(UpdateSubStockOrderLotCommand command) {
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

    State on(UpdatedSubStockOrderLotEvent event) {
      if (isEmpty()) {
        var newStockOrderLot = new StockOrderLot(event.subStockOrderLotId().levelUp(), 0, 0, List.of());
        return new State(newStockOrderLot.addSubLot(event.subStockOrderLot), true);
      }

      return new State(stockOrderLot.addSubLot(event.subStockOrderLot), true);
    }

    State on(UpdatedStockOrderLotEvent event) {
      return this;
    }

    State on(ReleasedStockOrderLotEvent event) {
      return new State(stockOrderLot, false);
    }
  }

  public interface Event {}

  public record UpdateSubStockOrderLotCommand(StockOrderLotId subStockOrderLotId, StockOrderLot subStockOrderLot) {}

  public record UpdatedSubStockOrderLotEvent(StockOrderLotId subStockOrderLotId, StockOrderLot subStockOrderLot) implements Event {}

  public record UpdatedStockOrderLotEvent(StockOrderLotId stockOrderLotId) implements Event {}

  public record ReleaseStockOrderLotCommand(StockOrderLotId stockOrderLotId) {}

  public record ReleasedStockOrderLotEvent(StockOrderLotId stockOrderLotId, StockOrderLot stockOrderLot) implements Event {}
}
