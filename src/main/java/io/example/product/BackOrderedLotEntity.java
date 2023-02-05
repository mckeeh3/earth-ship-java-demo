package io.example.product;

import java.util.List;

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
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("backOrderedLotId")
@EntityType("backOrderedLot")
@RequestMapping("/back-ordered-lot/{backOrderedLotId}")
public class BackOrderedLotEntity extends EventSourcedEntity<BackOrderedLotEntity.State> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public BackOrderedLotEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/update")
  public Effect<String> create(@RequestBody UpdateSubBackOrderedLotCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/release")
  public Effect<String> releaseBackOrderedLot(@RequestBody ReleaseBackOrderedLotCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetBackOrderedLot", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "BackOrderedLot not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(UpdatedSubBackOrderedLotEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedBackOrderedLotEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedBackOrderedLotEvent event) {
    return currentState().on(event);
  }

  public record State(
      BackOrderedLot backOrderedLot,
      boolean hasChanged) {

    static State emptyState() {
      return new State(null, false);
    }

    boolean isEmpty() {
      return backOrderedLot == null;
    }

    List<?> eventFor(UpdateSubBackOrderedLotCommand command) {
      if (hasChanged) {
        return List.of(new UpdatedSubBackOrderedLotEvent(command.subBackOrderedLotId(), command.subBackOrderedLot()));
      } else {
        var upperBackOrderedLotId = command.subBackOrderedLotId().levelUp();
        return List.of(
            new UpdatedSubBackOrderedLotEvent(command.subBackOrderedLotId(), command.subBackOrderedLot()),
            new UpdatedBackOrderedLotEvent(upperBackOrderedLotId));
      }
    }

    ReleasedBackOrderedLotEvent eventFor(ReleaseBackOrderedLotCommand command) {
      return new ReleasedBackOrderedLotEvent(backOrderedLot.backOrderedLotId(), backOrderedLot.copyWithoutSubLots());
    }

    State on(UpdatedSubBackOrderedLotEvent event) {
      if (isEmpty()) {
        var newBackOrderedLot = new BackOrderedLot(event.subBackOrderedLotId().levelUp(), 0, List.of());
        return new State(newBackOrderedLot.addSubLot(event.subBackOrderedLot()), true);
      }
      return new State(backOrderedLot.addSubLot(event.subBackOrderedLot), true);
    }

    State on(UpdatedBackOrderedLotEvent event) {
      return this;
    }

    State on(ReleasedBackOrderedLotEvent event) {
      return new State(backOrderedLot, false);
    }
  }

  public record UpdateSubBackOrderedLotCommand(BackOrderedLotId subBackOrderedLotId, BackOrderedLot subBackOrderedLot) {}

  public record UpdatedSubBackOrderedLotEvent(BackOrderedLotId subBackOrderedLotId, BackOrderedLot subBackOrderedLot) {}

  public record UpdatedBackOrderedLotEvent(BackOrderedLotId backOrderedLotId) {}

  public record ReleaseBackOrderedLotCommand(BackOrderedLotId backOrderedLotId) {}

  public record ReleasedBackOrderedLotEvent(BackOrderedLotId backOrderedLotId, BackOrderedLot backOrderedLot) {}
}
