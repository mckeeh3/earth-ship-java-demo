package io.example.shipping;

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

@Id("orderItemId")
@TypeId("orderItemRedTree")
@RequestMapping("/order-item-red-tree/{orderItemId}")
public class OrderItemRedTreeEntity extends EventSourcedEntity<OrderItemRedTreeEntity.State, OrderItemRedTreeEntity.Event> {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String entityId;

  public OrderItemRedTreeEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.emptyState();
  }

  @PutMapping("/order-item-create")
  public Effect<String> orderItemCreate(@RequestBody OrderItemRedTreeCreateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PatchMapping("/order-item-sub-branch-update")
  public Effect<String> orderItemSubBranchUpdate(@RequestBody OrderItemSubBranchUpdateCommand command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping
  public Effect<State> get() {
    log.info("EntityId: {}\n_State: {}", entityId, currentState());
    return Validator.<Effect<State>>start()
        .isFalse(currentState().alreadyCreated(), "OrderItemRedTreeEntity '%s' not found".formatted(entityId))
        .onError(errorMessage -> effects().error(errorMessage, Status.Code.INVALID_ARGUMENT))
        .onSuccess(() -> effects().reply(currentState()));
  }

  @EventHandler
  public State on(OrderItemRedTreeCreatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemSubBranchUpdatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(OrderItemSubBranchParentUpdatedEvent event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      OrderItemRedTreeId orderItemRedTreeId,
      OrderItemRedTreeId parentId,
      int quantity,
      int quantityReadyToShip,
      int quantityBackOrdered,
      List<SubBranch> subBranches) {

    static State emptyState() {
      return new State(null, null, 0, 0, 0, List.of());
    }

    boolean alreadyCreated() {
      return orderItemRedTreeId != null;
    }

    Event eventFor(OrderItemRedTreeCreateCommand command) {
      return new OrderItemRedTreeCreatedEvent(
          command.orderItemRedTreeId(),
          command.parentId(),
          command.quantity(),
          SubBranch.subBranchesOf(command.orderItemRedTreeId, command.quantity));
    }

    List<Event> eventsFor(OrderItemSubBranchUpdateCommand command) {
      var updatedSubBranch = new SubBranch(
          command.orderItemRedTreeId(),
          command.parentId(),
          command.quantity(),
          command.quantityReadyToShip(),
          command.quantityBackOrdered());
      var filteredSubBranches = subBranches.stream()
          .filter(subBranch -> !subBranch.orderItemRedTreeId().equals(command.orderItemRedTreeId()))
          .toList();
      var newSubBranches = Stream.concat(filteredSubBranches.stream(), Stream.of(updatedSubBranch))
          .toList();
      var reduced = SubBranch.reduce(newSubBranches);

      var event = new OrderItemSubBranchUpdatedEvent(
          command.orderItemRedTreeId,
          command.parentId,
          command.quantity,
          command.quantityReadyToShip,
          command.quantityBackOrdered);
      var parentEvent = parentId == null
          ? null
          : new OrderItemSubBranchParentUpdatedEvent(
              orderItemRedTreeId,
              parentId,
              reduced.quantity(),
              reduced.quantityReadyToShip(),
              reduced.quantityBackOrdered());

      return parentEvent == null
          ? List.of(event)
          : List.of(event, parentEvent);
    }

    State on(OrderItemRedTreeCreatedEvent event) {
      return new State(event.orderItemRedTreeId(), event.parentId(), event.quantity(), 0, 0, event.suBranches);
    }

    State on(OrderItemSubBranchUpdatedEvent event) {
      var updatedSubBranch = new SubBranch(
          event.orderItemRedTreeId(),
          orderItemRedTreeId,
          event.quantity(),
          event.quantityReadyToShip(),
          event.quantityBackOrdered());
      var filteredSubBranches = subBranches.stream()
          .filter(subBranch -> !subBranch.orderItemRedTreeId().equals(event.orderItemRedTreeId()))
          .toList();
      var newSubBranches = Stream.concat(filteredSubBranches.stream(), Stream.of(updatedSubBranch))
          .toList();
      var reduced = SubBranch.reduce(newSubBranches);

      return new State(
          orderItemRedTreeId,
          parentId,
          quantity,
          reduced.quantityReadyToShip(),
          reduced.quantityBackOrdered(),
          newSubBranches);
    }

    State on(OrderItemSubBranchParentUpdatedEvent event) {
      return this;
    }
  }

  public record OrderItemRedTreeId(String orderId, String skuId, UUID uuId) {
    public static OrderItemRedTreeId of(String orderId, String skuId) {
      return new OrderItemRedTreeId(orderId, skuId, UUID.randomUUID());
    }

    public String toEntityId() {
      return "%s_%s_%s".formatted(orderId, skuId, uuId);
    }
  }

  public record SubBranch(
      OrderItemRedTreeId orderItemRedTreeId,
      OrderItemRedTreeId parentId,
      int quantity,
      int quantityReadyToShip,
      int quantityBackOrdered) {

    static final int maxSubBranchesPerBranch = 10;
    static final int maxLeavesPerBranch = maxSubBranchesPerBranch * 2;

    static List<SubBranch> subBranchesOf(OrderItemRedTreeId orderItemRedTreeId, int quantity) {
      if (quantity <= maxLeavesPerBranch) {
        return List.of(new SubBranch(
            OrderItemRedTreeId.of(orderItemRedTreeId.orderId(), orderItemRedTreeId.skuId()),
            orderItemRedTreeId,
            quantity,
            0,
            0));
      }

      var quantities = subBranchQuantities(quantity);
      return quantities.stream()
          .map(quantitySubBranch -> new SubBranch(
              OrderItemRedTreeId.of(orderItemRedTreeId.orderId(), orderItemRedTreeId.skuId()),
              orderItemRedTreeId,
              quantitySubBranch,
              0,
              0))
          .toList();
    }

    static List<Integer> subBranchQuantities(int quantity) {
      var subBranchesCount = (int) Math.min(maxSubBranchesPerBranch, Math.ceil((double) quantity / maxSubBranchesPerBranch));
      var leavesPerBranch = (int) Math.ceil((double) quantity / subBranchesCount);
      var range = (int) Math.min(maxSubBranchesPerBranch, Math.ceil((double) quantity / maxSubBranchesPerBranch));

      return IntStream.rangeClosed(1, range)
          .mapToObj(i -> (int) (i * leavesPerBranch > quantity
              ? quantity - (i - 1) * leavesPerBranch
              : leavesPerBranch))
          .toList();
    };

    static SubBranch reduce(List<SubBranch> subBranches) {
      return subBranches.stream()
          .reduce(
              new SubBranch(null, null, 0, 0, 0),
              (acc, subBranch) -> new SubBranch(
                  subBranch.orderItemRedTreeId(),
                  subBranch.parentId(),
                  acc.quantity() + subBranch.quantity(),
                  acc.quantityReadyToShip() + subBranch.quantityReadyToShip(),
                  acc.quantityBackOrdered() + subBranch.quantityBackOrdered()),
              (acc1, acc2) -> new SubBranch(
                  acc1.orderItemRedTreeId(),
                  acc1.parentId(),
                  acc1.quantity() + acc2.quantity(),
                  acc1.quantityReadyToShip() + acc2.quantityReadyToShip(),
                  acc1.quantityBackOrdered() + acc2.quantityBackOrdered()));
    }
  }

  public interface Event {}

  public record OrderItemRedTreeCreateCommand(OrderItemRedTreeId orderItemRedTreeId, OrderItemRedTreeId parentId, int quantity) {}

  public record OrderItemRedTreeCreatedEvent(OrderItemRedTreeId orderItemRedTreeId, OrderItemRedTreeId parentId, int quantity, List<SubBranch> suBranches) implements Event {}

  public record OrderItemSubBranchUpdateCommand(OrderItemRedTreeId orderItemRedTreeId, OrderItemRedTreeId parentId, int quantity, int quantityReadyToShip, int quantityBackOrdered) {}

  public record OrderItemSubBranchUpdatedEvent(OrderItemRedTreeId orderItemRedTreeId, OrderItemRedTreeId parentId, int quantity, int quantityReadyToShip, int quantityBackOrdered) implements Event {}

  public record OrderItemSubBranchParentUpdatedEvent(OrderItemRedTreeId orderItemRedTreeId, OrderItemRedTreeId parentId, int quantity, int quantityReadyToShip, int quantityBackOrdered) implements Event {}
}
