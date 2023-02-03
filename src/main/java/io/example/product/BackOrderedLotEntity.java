package io.example.product;

import java.util.List;

import javax.naming.ldap.HasControls;

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

@EntityKey("backOrderedLotId")
@EntityType("backOrderedLot")
@RequestMapping("/backOrderedLot/{backOrderedLotId}")
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

  @PostMapping("/updateSubBackOrderedLot")
  public Effect<String> create(@RequestBody UpdateSubBackOrderLotCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/releaseBackOrderedLot")
  public Effect<String> releaseBackOrderedLot(@RequestBody ReleaseBackOrderLotCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetBackOrderLot", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isTrue(currentState().isEmpty(), "BackOrderLot not found")
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(UpdatedSubBackOrderLotEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedBackOrderLotEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedBackOrderLotEvent event) {
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

    List<?> eventFor(UpdateSubBackOrderLotCommand command) {
      if (hasChanged) {
        return List.of(new UpdatedSubBackOrderLotEvent(command.subBackOrderedLotId(), command.subBackOrderedLot()));
      } else {
        var backOrderedLotId = command.subBackOrderedLotId().levelUp();
        return List.of(
            new UpdatedSubBackOrderLotEvent(command.subBackOrderedLotId(), command.subBackOrderedLot()),
            new UpdatedBackOrderLotEvent(backOrderedLotId));
      }
    }

    ReleasedBackOrderLotEvent eventFor(ReleaseBackOrderLotCommand command) {
      return new ReleasedBackOrderLotEvent(backOrderedLot.backOrderedLotId(), backOrderedLot.copyWithoutSubLots());
    }

    State on(UpdatedSubBackOrderLotEvent event) {
      if (isEmpty()) {
        var newBackOrderedLot = new BackOrderedLot(event.subBackOrderedLotId().levelUp(), 0, List.of());
        return new State(newBackOrderedLot.addSubLot(event.subBackOrderedLot()), true);
      }
      return new State(backOrderedLot.addSubLot(event.subBackOrderedLot), true);
    }

    State on(UpdatedBackOrderLotEvent event) {
      return this;
    }

    State on(ReleasedBackOrderLotEvent event) {
      return new State(event.backOrderedLot(), false);
    }
  }

  public record UpdateSubBackOrderLotCommand(BackOrderedLotId subBackOrderedLotId, BackOrderedLot subBackOrderedLot) {}

  public record UpdatedSubBackOrderLotEvent(BackOrderedLotId subBackOrderedLotId, BackOrderedLot subBackOrderedLot) {}

  public record UpdatedBackOrderLotEvent(BackOrderedLotId backOrderedLotId) {}

  public record ReleaseBackOrderLotCommand(BackOrderedLotId backOrderedLotId) {}

  public record ReleasedBackOrderLotEvent(BackOrderedLotId backOrderedLotId, BackOrderedLot backOrderedLot) {}
}
