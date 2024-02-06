package io.example.product;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.Validator;
import io.example.shipping.OrderItemRedLeafEntity;
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;

@Id("backOrderedRedTreeId")
@TypeId("backOrderedRedTree")
@RequestMapping("/back-ordered-red-tree/{backOrderedRedTreeId}")
public class BackOrderedRedTreeEntity extends EventSourcedEntity<BackOrderedRedTreeEntity.State, BackOrderedRedTreeEntity.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public BackOrderedRedTreeEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/update")
  public Effect<String> updateSubBranch(@RequestBody UpdateSubBranchCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/release")
  public Effect<String> releaseToParent(@RequestBody ReleaseToParentCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}\n_GetBackOrderedRedTree", entityId, currentState());
    return Validator
        .isTrue(currentState().isEmpty(), "BackOrderedRedTree not found")
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND));
  }

  @EventHandler
  public State on(UpdatedSubBranchEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedBranchEvent event) {
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedToParentEvent event) {
    return currentState().on(event);
  }

  public record State(
      BackOrderedRedTreeId backOrderedRedTreeId,
      boolean hasChanged,
      List<SubBranch> subBranches) {

    static State emptyState() {
      return new State(null, false, List.of());
    }

    boolean isEmpty() {
      return backOrderedRedTreeId == null;
    }

    List<? extends Event> eventsFor(UpdateSubBranchCommand command) {
      var filteredSubBranches = subBranches.stream()
          .filter(subBranch -> !subBranch.backOrderedRedTreeId().equals(command.subBranch().backOrderedRedTreeId()))
          .filter(subBranch -> subBranch.quantityBackOrdered() > 0)
          .toList();
      var newSubBranch = new SubBranch(command.subBranch().backOrderedRedTreeId(), command.subBranch().quantityBackOrdered());
      var newSubBranches = newSubBranch.quantityBackOrdered() > 0
          ? Stream.concat(filteredSubBranches.stream(), Stream.of(newSubBranch)).toList()
          : filteredSubBranches;
      var event = new UpdatedSubBranchEvent(command.subBranchId(), command.parentId(), newSubBranch, true, newSubBranches);

      return hasChanged
          ? List.of(event)
          : List.of(
              event,
              new UpdatedBranchEvent(command.parentId()));
    }

    Event eventFor(ReleaseToParentCommand command) {
      var subBranch = SubBranch.reduce(backOrderedRedTreeId, subBranches);

      return new ReleasedToParentEvent(command.subBranchId(), command.parentId(), subBranch);
    }

    State on(UpdatedSubBranchEvent event) {
      return new State(event.parentId(), event.hasChanged, event.subBranches());
    }

    State on(UpdatedBranchEvent event) {
      return this;
    }

    State on(ReleasedToParentEvent event) {
      return new State(event.parentId, false, subBranches);
    }
  }

  public record BackOrderedRedTreeId(String skuId, int branchLevel, int branchNumber, OrderItemRedLeafEntity.OrderItemRedLeafId orderItemRedLeafId) {

    static final int subBranchesPerBranch = 100;
    static final int totalLeaves = 1_000_000;
    static final int leafLevel = (int) Math.ceil(Math.log(totalLeaves) / Math.log(subBranchesPerBranch));

    String toEntityId() {
      return "%s_%s_%d_%d".formatted(orderItemRedLeafId == null ? "Y" : orderItemRedLeafId, skuId, branchLevel, branchNumber);
    }

    static BackOrderedRedTreeId of(String skuId, int leafLevel, int leafNumber) {
      return new BackOrderedRedTreeId(skuId, leafLevel, leafNumber, null);
    }

    static BackOrderedRedTreeId of(OrderItemRedLeafEntity.OrderItemRedLeafId orderItemRedLeafId) {
      var leafNumber = Math.abs(orderItemRedLeafId.toEntityId().hashCode()) % totalLeaves;
      return new BackOrderedRedTreeId(orderItemRedLeafId.skuId(), leafLevel, leafNumber, orderItemRedLeafId);
    }

    BackOrderedRedTreeId levelDown() {
      if (branchLevel == 0) {
        return this;
      }
      var newBranchLevel = branchLevel - 1;
      var newBranchNumber = branchNumber / subBranchesPerBranch;

      return BackOrderedRedTreeId.of(skuId, newBranchLevel, newBranchNumber);
    }
  }

  public record SubBranch(BackOrderedRedTreeId backOrderedRedTreeId, int quantityBackOrdered) {

    static SubBranch reduce(BackOrderedRedTreeId backOrderedRedTreeId, List<SubBranch> subBranches) {
      var quantityBackOrdered = subBranches.stream()
          .mapToInt(subBranch -> subBranch.quantityBackOrdered())
          .sum();
      return new SubBranch(backOrderedRedTreeId, quantityBackOrdered);
    }
  }

  public interface Event {}

  public record UpdateSubBranchCommand(BackOrderedRedTreeId subBranchId, BackOrderedRedTreeId parentId, SubBranch subBranch) {}

  public record UpdatedSubBranchEvent(BackOrderedRedTreeId subBranchId, BackOrderedRedTreeId parentId, SubBranch subBranch, boolean hasChanged, List<SubBranch> subBranches) implements Event {}

  public record UpdatedBranchEvent(BackOrderedRedTreeId backOrderedRedTreeId) implements Event {}

  public record ReleaseToParentCommand(BackOrderedRedTreeId subBranchId, BackOrderedRedTreeId parentId) {}

  public record ReleasedToParentEvent(BackOrderedRedTreeId subBranchId, BackOrderedRedTreeId parentId, SubBranch subBranch) implements Event {}
}