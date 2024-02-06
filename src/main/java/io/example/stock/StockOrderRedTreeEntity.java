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
import io.grpc.Status;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;

@Id("stockOrderRedTreeId")
@TypeId("stockOrderRedTree")
@RequestMapping("/stock-order-red-tree/{stockOrderRedTreeId}")
public class StockOrderRedTreeEntity extends EventSourcedEntity<StockOrderRedTreeEntity.State, StockOrderRedTreeEntity.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public StockOrderRedTreeEntity(EventSourcedEntityContext context) {
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
    log.info("EntityId: {}\n_State: {}\n_GetStockOrderRedTree", entityId, currentState());
    return Validator
        .isTrue(currentState().isEmpty(), "StockOrderRedTree not found")
        .onSuccess(() -> effects().reply(currentState()))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.NOT_FOUND));
  }

  @EventHandler
  public State on(UpdatedSubBranchEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(UpdatedBranchEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(ReleasedToParentEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      StockOrderRedTreeId stockOrderRedTreeId,
      boolean hasChanged,
      List<SubBranch> subBranches) {

    static State emptyState() {
      return new State(null, false, List.of());
    }

    boolean isEmpty() {
      return stockOrderRedTreeId == null;
    }

    List<? extends Event> eventsFor(UpdateSubBranchCommand command) {
      var filteredSubBranches = subBranches.stream()
          .filter(subBranch -> !subBranch.stockOrderRedTreeId().equals(command.subBranch().stockOrderRedTreeId()))
          .filter(subBranch -> subBranch.quantityConsumed() > 0)
          .toList();
      var newSubBranch = new SubBranch(command.subBranch().stockOrderRedTreeId(), command.subBranch().quantityConsumed());
      var newSubBranches = newSubBranch.quantityConsumed() > 0
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
      var subBranch = SubBranch.reduce(stockOrderRedTreeId, subBranches);

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

  public record StockOrderRedTreeId(String stockOrderId, String skuId, int branchLevel, int branchNumber, StockOrderRedLeafEntity.StockOrderRedLeafId orderItemRedLeafId) {

    static final int subBranchesPerBranch = 100;

    String toEntityId() {
      return "%s_%s_%s_%d_%d".formatted(orderItemRedLeafId == null ? "Y" : orderItemRedLeafId, stockOrderId, skuId, branchLevel, branchNumber);
    }

    static StockOrderRedTreeId of(StockOrderRedLeafEntity.StockOrderRedLeafId orderItemRedLeafId) {
      var stockOrderId = orderItemRedLeafId.stockOrderId();
      var skuId = orderItemRedLeafId.skuId();
      var leafLevel = orderItemRedLeafId.branchLevel();
      var leafNumber = orderItemRedLeafId.branchNumber();
      return new StockOrderRedTreeId(stockOrderId, skuId, leafLevel, leafNumber, orderItemRedLeafId);
    }

    StockOrderRedTreeId levelDown() {
      if (branchLevel == 0) {
        return this;
      }
      var newBranchLevel = branchLevel - 1;
      var newBranchNumber = branchNumber / subBranchesPerBranch;

      return new StockOrderRedTreeId(stockOrderId, skuId, newBranchLevel, newBranchNumber, null);
    }
  }

  public record SubBranch(StockOrderRedTreeId stockOrderRedTreeId, int quantityConsumed) {

    static SubBranch reduce(StockOrderRedTreeId stockOrderRedTreeId, List<SubBranch> subBranches) {
      var quantityStockOrder = subBranches.stream()
          .mapToInt(subBranch -> subBranch.quantityConsumed())
          .sum();
      return new SubBranch(stockOrderRedTreeId, quantityStockOrder);
    }
  }

  public interface Event {}

  public record UpdateSubBranchCommand(StockOrderRedTreeId subBranchId, StockOrderRedTreeId parentId, SubBranch subBranch) {}

  public record UpdatedSubBranchEvent(StockOrderRedTreeId subBranchId, StockOrderRedTreeId parentId, SubBranch subBranch, boolean hasChanged, List<SubBranch> subBranches) implements Event {}

  public record UpdatedBranchEvent(StockOrderRedTreeId stockOrderRedTreeId) implements Event {}

  public record ReleaseToParentCommand(StockOrderRedTreeId subBranchId, StockOrderRedTreeId parentId) {}

  public record ReleasedToParentEvent(StockOrderRedTreeId subBranchId, StockOrderRedTreeId parentId, SubBranch subBranch) implements Event {}
}
