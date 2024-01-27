package io.example.shipping;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

class OrderItemRedTreeEntityTest {
  @Test
  void orderItemCreateTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedTreeEntity::new);
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of("orderId", "skuId");
    var quantity = 1247;

    {
      var command = new OrderItemRedTreeEntity.OrderItemRedTreeCreateCommand(orderItemRedTreeId, null, quantity);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedTreeEntity.OrderItemRedTreeCreatedEvent.class);
      assertEquals(orderItemRedTreeId, event.orderItemRedTreeId());
      assertNull(event.parentId());
      assertEquals(quantity, event.quantity());
      assertTrue(event.suBranches().size() > 0);

      var reduced = OrderItemRedTreeEntity.SubBranch.reduce(event.suBranches());
      assertEquals(quantity, reduced.quantity());
    }

    {
      var state = testKit.getState();
      assertEquals(orderItemRedTreeId, state.orderItemRedTreeId());
      assertNull(state.parentId());
      assertEquals(quantity, state.quantity());
      assertEquals(0, state.quantityReadyToShip());
      assertEquals(0, state.quantityBackOrdered());
      assertTrue(state.subBranches().size() > 0);
    }
  }

  @Test
  void getTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedTreeEntity::new);
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of("orderId", "skuId");
    var quantity = 1247;

    {
      var command = new OrderItemRedTreeEntity.OrderItemRedTreeCreateCommand(orderItemRedTreeId, null, quantity);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var getState = result.getReply();
      var state = testKit.getState();
      assertEquals(state, getState);
    }
  }

  @Test
  void updateOneSubBranchOnTrunkTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedTreeEntity::new);
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of("orderId", "skuId");

    var quantity = 1247;
    var quantityReadyToShip = 12;
    var quantityBackOrdered = 23;

    {
      var command = new OrderItemRedTreeEntity.OrderItemRedTreeCreateCommand(orderItemRedTreeId, null, quantity);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var subBranch = testKit.getState().subBranches().get(0);
      var command = new OrderItemRedTreeEntity.OrderItemSubBranchUpdateCommand(
          subBranch.orderItemRedTreeId(),
          subBranch.parentId(),
          subBranch.quantity(),
          quantityReadyToShip,
          quantityBackOrdered);

      var result = testKit.call(e -> e.orderItemSubBranchUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(1, eventCount);

      var event = result.getNextEventOfType(OrderItemRedTreeEntity.OrderItemSubBranchUpdatedEvent.class);
      assertEquals(subBranch.orderItemRedTreeId(), event.orderItemRedTreeId());
      assertEquals(orderItemRedTreeId, event.parentId());
      assertEquals(subBranch.quantity(), event.quantity());
      assertEquals(quantityReadyToShip, event.quantityReadyToShip());
      assertEquals(quantityBackOrdered, event.quantityBackOrdered());
    }

    {
      var state = testKit.getState();
      assertEquals(orderItemRedTreeId, state.orderItemRedTreeId());
      assertNull(state.parentId());
      assertEquals(quantity, state.quantity());
      assertEquals(quantityReadyToShip, state.quantityReadyToShip());
      assertEquals(quantityBackOrdered, state.quantityBackOrdered());
      assertTrue(state.subBranches().size() > 0);

      var reduced = OrderItemRedTreeEntity.SubBranch.reduce(state.subBranches());
      assertEquals(quantityReadyToShip, reduced.quantityReadyToShip());
      assertEquals(quantityBackOrdered, reduced.quantityBackOrdered());
    }
  }

  @Test
  void updateTwoSubBranchesOnNonTrunkBranchTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedTreeEntity::new);
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of("orderId", "skuId");
    var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.of("parent-orderId", "skuId");

    var quantity = 1247;
    var quantityReadyToShip1 = 12;
    var quantityBackOrdered1 = 23;
    var quantityReadyToShip2 = 34;
    var quantityBackOrdered2 = 45;

    {
      var command = new OrderItemRedTreeEntity.OrderItemRedTreeCreateCommand(orderItemRedTreeId, parentId, quantity);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var subBranchIdx = 0;
      var subBranch = testKit.getState().subBranches().get(subBranchIdx);
      var command = new OrderItemRedTreeEntity.OrderItemSubBranchUpdateCommand(
          subBranch.orderItemRedTreeId(),
          subBranch.parentId(),
          subBranch.quantity(),
          quantityReadyToShip1,
          quantityBackOrdered1);

      var result = testKit.call(e -> e.orderItemSubBranchUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount);

      var event = result.getNextEventOfType(OrderItemRedTreeEntity.OrderItemSubBranchUpdatedEvent.class);
      assertEquals(subBranch.orderItemRedTreeId(), event.orderItemRedTreeId());
      assertEquals(orderItemRedTreeId, event.parentId());
      assertEquals(subBranch.quantity(), event.quantity());
      assertEquals(quantityReadyToShip1, event.quantityReadyToShip());
      assertEquals(quantityBackOrdered1, event.quantityBackOrdered());

      var eventParent = result.getNextEventOfType(OrderItemRedTreeEntity.OrderItemSubBranchParentUpdatedEvent.class);
      assertEquals(orderItemRedTreeId, eventParent.orderItemRedTreeId());
      assertEquals(parentId, eventParent.parentId());
      assertEquals(quantity, eventParent.quantity());
      assertEquals(quantityReadyToShip1, eventParent.quantityReadyToShip());
      assertEquals(quantityBackOrdered1, eventParent.quantityBackOrdered());
    }

    {
      var subBranchIdx = 1;
      var subBranch = testKit.getState().subBranches().get(subBranchIdx);
      var command = new OrderItemRedTreeEntity.OrderItemSubBranchUpdateCommand(
          subBranch.orderItemRedTreeId(),
          subBranch.parentId(),
          subBranch.quantity(),
          quantityReadyToShip2,
          quantityBackOrdered2);

      var result = testKit.call(e -> e.orderItemSubBranchUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount);

      var event = result.getNextEventOfType(OrderItemRedTreeEntity.OrderItemSubBranchUpdatedEvent.class);
      assertEquals(subBranch.orderItemRedTreeId(), event.orderItemRedTreeId());
      assertEquals(orderItemRedTreeId, event.parentId());
      assertEquals(subBranch.quantity(), event.quantity());
      assertEquals(quantityReadyToShip2, event.quantityReadyToShip());
      assertEquals(quantityBackOrdered2, event.quantityBackOrdered());

      var eventParent = result.getNextEventOfType(OrderItemRedTreeEntity.OrderItemSubBranchParentUpdatedEvent.class);
      assertEquals(orderItemRedTreeId, eventParent.orderItemRedTreeId());
      assertEquals(parentId, eventParent.parentId());
      assertEquals(quantity, eventParent.quantity());
      assertEquals(quantityReadyToShip1 + quantityReadyToShip2, eventParent.quantityReadyToShip());
      assertEquals(quantityBackOrdered1 + quantityBackOrdered2, eventParent.quantityBackOrdered());
    }
  }

  @Test
  void subBranchTest() {
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of("orderId-1", "skuId-1");

    var quantity = 1247;
    var subBranches = OrderItemRedTreeEntity.SubBranch.subBranchesOf(orderItemRedTreeId, quantity);
    var reduced = OrderItemRedTreeEntity.SubBranch.reduce(subBranches);
    assertEquals(quantity, reduced.quantity());
  }

  @Test
  void testSubBranchCreation() {
    testSubBranchCreation(2);
    testSubBranchCreation(9);
    testSubBranchCreation(10);
    testSubBranchCreation(12);
    testSubBranchCreation(19);
    testSubBranchCreation(20);
    testSubBranchCreation(21);
    testSubBranchCreation(22);
    testSubBranchCreation(29);
    testSubBranchCreation(39);
    testSubBranchCreation(40);
    testSubBranchCreation(41);
    testSubBranchCreation(42);
    testSubBranchCreation(1247);
    testSubBranchCreation(125);
    testSubBranchCreation(122);

    var subBranch = testSubBranchCreation(12579);
    while (subBranch.isPresent()) {
      subBranch = testSubBranchCreation(subBranch.get().quantity());
    }
  }

  Optional<OrderItemRedTreeEntity.SubBranch> testSubBranchCreation(int quantity) {
    var orderItemRedTreeId = OrderItemRedTreeEntity.OrderItemRedTreeId.of("orderId-1", "skuId-1");
    var subBranches = OrderItemRedTreeEntity.SubBranch.subBranchesOf(orderItemRedTreeId, quantity);
    var reduced = OrderItemRedTreeEntity.SubBranch.reduce(subBranches);

    assertEquals(quantity, reduced.quantity());
    assertTrue(quantity <= OrderItemRedTreeEntity.SubBranch.maxLeavesPerBranch && subBranches.size() == 1
        || quantity > OrderItemRedTreeEntity.SubBranch.maxLeavesPerBranch && subBranches.size() > 1);

    return quantity <= OrderItemRedTreeEntity.SubBranch.maxLeavesPerBranch ? Optional.empty() : Optional.of(subBranches.get(0));
  }
}