package io.example.stock;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
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
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("stockOrderRedTreeId")
@TypeId("stockOrderRedTree")
@RequestMapping("/stock-order-red-tree/{stockOrderRedTreeId}")
public class StockOrderRedTreeEntity extends EventSourcedEntity<StockOrderRedTreeEntity.State, StockOrderRedTreeEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(StockOrderRedTreeEntity.class);
  private final String entityId;

  public StockOrderRedTreeEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/stock-order-create")
  public Effect<String> stockOrderCreate(@RequestBody StockOrderRedTreeCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/stock-order-sub-branch-update")
  public Effect<String> stockOrderSubBranchUpdate(@RequestBody StockOrderSubBranchUpdateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isFalse(currentState().alreadyCreated(), "StockOrderRedTreeEntity '%s' not found".formatted(entityId))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(StockOrderRedTreeCreatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderSubBranchUpdatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(StockOrderSubBranchParentUpdatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      StockOrderRedTreeId stockOrderRedTreeId,
      StockOrderRedTreeId parentId,
      int quantity,
      int quantityOrdered,
      List<SubBranch> subBranches) {

    public static State emptyState() {
      return new State(null, null, 0, 0, List.of());
    }

    public boolean alreadyCreated() {
      return stockOrderRedTreeId() != null;
    }

    List<Event> eventsFor(StockOrderRedTreeCreateCommand command) {
      if (alreadyCreated()) {
        return List.of();
      }

      return List.of(
          new StockOrderRedTreeCreatedEvent(
              command.stockOrderRedTreeId(),
              command.parentId(),
              command.quantity(),
              SubBranch.subBranchesOf(command.stockOrderRedTreeId(), command.quantity())));
    }

    List<Event> eventsFor(StockOrderSubBranchUpdateCommand command) {
      var updatedSubBranch = new SubBranch(
          command.stockOrderRedTreeId(),
          command.parentId(),
          command.quantity(),
          command.quantityOrdered());
      var filteredSubBranches = subBranches.stream()
          .filter(subBranch -> !subBranch.stockOrderRedTreeId().equals(command.stockOrderRedTreeId()))
          .toList();
      var newSubBranches = Stream.concat(filteredSubBranches.stream(), Stream.of(updatedSubBranch))
          .toList();
      var reduced = SubBranch.reduce(newSubBranches);

      var event = new StockOrderSubBranchUpdatedEvent(
          command.stockOrderRedTreeId,
          command.parentId,
          command.quantity,
          command.quantityOrdered);
      var parentEvent = parentId == null
          ? null
          : new StockOrderSubBranchParentUpdatedEvent(
              stockOrderRedTreeId,
              parentId,
              reduced.quantity(),
              reduced.quantityOrdered());

      return parentEvent == null
          ? List.of(event)
          : List.of(event, parentEvent);
    }

    State on(StockOrderRedTreeCreatedEvent event) {
      return new State(event.stockOrderRedTreeId(), event.parentId(), event.quantity(), 0, event.subBranches());
    }

    State on(StockOrderSubBranchUpdatedEvent event) {
      var updatedSubBranch = new SubBranch(
          event.stockOrderRedTreeId(),
          stockOrderRedTreeId,
          event.quantity(),
          event.quantityOrdered());
      var filteredSubBranches = subBranches.stream()
          .filter(subBranch -> !subBranch.stockOrderRedTreeId().equals(event.stockOrderRedTreeId()))
          .toList();
      var newSubBranches = Stream.concat(filteredSubBranches.stream(), Stream.of(updatedSubBranch))
          .toList();
      var reduced = SubBranch.reduce(newSubBranches);

      return new State(
          stockOrderRedTreeId,
          parentId,
          quantity,
          reduced.quantityOrdered(),
          newSubBranches);
    }

    State on(StockOrderSubBranchParentUpdatedEvent event) {
      return this;
    }
  }

  public record StockOrderRedTreeId(String orderId, String skuId, UUID uuId) {
    public static StockOrderRedTreeId of(String orderId, String skuId) {
      return new StockOrderRedTreeId(orderId, skuId, UUID.randomUUID());
    }

    public String toEntityId() {
      return "%s_%s_%s".formatted(orderId, skuId, uuId);
    }
  }

  public record SubBranch(
      StockOrderRedTreeId stockOrderRedTreeId,
      StockOrderRedTreeId parentId,
      int quantity,
      int quantityOrdered) {

    static final int maxSubBranchesPerBranch = 10;
    static final int maxLeavesPerBranch = maxSubBranchesPerBranch * 2;

    static List<SubBranch> subBranchesOf(StockOrderRedTreeId stockOrderRedTreeId, int quantity) {
      if (quantity <= maxLeavesPerBranch) {
        return List.of(new SubBranch(
            StockOrderRedTreeId.of(stockOrderRedTreeId.orderId(), stockOrderRedTreeId.skuId()),
            stockOrderRedTreeId,
            quantity,
            0));
      }

      var quantities = subBranchQuantities(quantity);
      return quantities.stream()
          .map(quantitySubBranch -> new SubBranch(
              StockOrderRedTreeId.of(stockOrderRedTreeId.orderId(), stockOrderRedTreeId.skuId()),
              stockOrderRedTreeId,
              quantitySubBranch,
              0))
          .toList();
    }

    static List<Integer> subBranchQuantities(int quantity) {
      var subBranchesCount = (int) Math.min(maxSubBranchesPerBranch, Math.ceil((double) quantity / maxSubBranchesPerBranch));
      var leavesPerBranch = (int) Math.max(maxLeavesPerBranch, Math.ceil((double) quantity / subBranchesCount));
      var range = (int) Math.min(maxSubBranchesPerBranch, Math.ceil((double) quantity / maxSubBranchesPerBranch));

      return IntStream.rangeClosed(1, range)
          .mapToObj(i -> (int) (i * leavesPerBranch > quantity
              ? quantity - (i - 1) * leavesPerBranch
              : leavesPerBranch))
          .filter(qty -> qty > 0)
          .toList();
    };

    static SubBranch reduce(List<SubBranch> subBranches) {
      return subBranches.stream()
          .reduce(
              new SubBranch(null, null, 0, 0),
              (acc, subBranch) -> new SubBranch(
                  subBranch.stockOrderRedTreeId(),
                  subBranch.parentId(),
                  acc.quantity() + subBranch.quantity(),
                  acc.quantityOrdered() + subBranch.quantityOrdered()),
              (acc1, acc2) -> new SubBranch(
                  acc1.stockOrderRedTreeId(),
                  acc1.parentId(),
                  acc1.quantity() + acc2.quantity(),
                  acc1.quantityOrdered() + acc2.quantityOrdered()));
    }
  }

  public interface Event {}

  public record StockOrderRedTreeCreateCommand(StockOrderRedTreeId stockOrderRedTreeId, StockOrderRedTreeId parentId, int quantity) {}

  public record StockOrderRedTreeCreatedEvent(StockOrderRedTreeId stockOrderRedTreeId, StockOrderRedTreeId parentId, int quantity, List<SubBranch> subBranches) implements Event {}

  public record StockOrderSubBranchUpdateCommand(StockOrderRedTreeId stockOrderRedTreeId, StockOrderRedTreeId parentId, int quantity, int quantityOrdered) {}

  public record StockOrderSubBranchUpdatedEvent(StockOrderRedTreeId stockOrderRedTreeId, StockOrderRedTreeId parentId, int quantity, int quantityOrdered) implements Event {}

  public record StockOrderSubBranchParentUpdatedEvent(StockOrderRedTreeId stockOrderRedTreeId, StockOrderRedTreeId parentId, int quantity, int quantityOrdered) implements Event {}
}