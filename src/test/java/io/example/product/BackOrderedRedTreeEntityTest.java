package io.example.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

import io.example.shipping.OrderItemRedLeafEntity;
import kalix.javasdk.testkit.EventSourcedTestKit;

public class BackOrderedRedTreeEntityTest {
  @Test
  void updateOneSubBranch() {
    var testKit = EventSourcedTestKit.of(BackOrderedRedTreeEntity::new);

    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId", "skuId");
    var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
    var parentId = subBranchId.levelDown();
    var quantityBackOrdered = 12;
    var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered);
    var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);

    {
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(BackOrderedRedTreeEntity.UpdatedSubBranchEvent.class);
        assertEquals(parentId, event.parentId());
        assertEquals(subBranch, event.subBranch());

        var subBranches = event.subBranches();
        var quantityBackOrderedEvent = subBranches.stream()
            .mapToInt(BackOrderedRedTreeEntity.SubBranch::quantityBackOrdered)
            .sum();
        assertEquals(quantityBackOrdered, quantityBackOrderedEvent);
      }

      {
        var event = result.getNextEventOfType(BackOrderedRedTreeEntity.UpdatedBranchEvent.class);
        assertEquals(parentId, event.backOrderedRedTreeId());
      }
    }

    var stateBefore = testKit.getState();
    {
      assertEquals(parentId, stateBefore.backOrderedRedTreeId());
      assertTrue(stateBefore.hasChanged());
      var quantityBackOrderedState = stateBefore.subBranches().stream()
          .mapToInt(BackOrderedRedTreeEntity.SubBranch::quantityBackOrdered)
          .sum();
      assertEquals(quantityBackOrdered, quantityBackOrderedState);
    }

    { // Idempotent test
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(BackOrderedRedTreeEntity.UpdatedSubBranchEvent.class);
        assertEquals(subBranchId, event.subBranchId());
        assertEquals(parentId, event.parentId());
        assertEquals(subBranch, event.subBranch());

        var subBranches = event.subBranches();
        var quantityBackOrderedEvent = subBranches.stream()
            .mapToInt(BackOrderedRedTreeEntity.SubBranch::quantityBackOrdered)
            .sum();
        assertEquals(quantityBackOrdered, quantityBackOrderedEvent);
      }

      var stateAfter = testKit.getState();
      assertEquals(stateBefore, stateAfter);
    }
  }

  @Test
  void updateTwoSubBranchesTest() {
    var testKit = EventSourcedTestKit.of(BackOrderedRedTreeEntity::new);

    var quantityBackOrdered1 = 12;
    var quantityBackOrdered2 = 23;
    var parentId = BackOrderedRedTreeEntity.BackOrderedRedTreeId
        .of(OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId", "skuId"))
        .levelDown();

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId-1", "skuId");
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered1);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(BackOrderedRedTreeEntity.UpdatedSubBranchEvent.class);
        assertEquals(subBranchId, event.subBranchId());
        assertEquals(parentId, event.parentId());
        assertEquals(subBranch, event.subBranch());

        var subBranches = event.subBranches();
        var quantityBackOrderedEvent = subBranches.stream()
            .mapToInt(BackOrderedRedTreeEntity.SubBranch::quantityBackOrdered)
            .sum();
        assertEquals(quantityBackOrdered1, quantityBackOrderedEvent);
      }

      {
        var event = result.getNextEventOfType(BackOrderedRedTreeEntity.UpdatedBranchEvent.class);
        assertEquals(parentId, event.backOrderedRedTreeId());
      }
    }

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId-2", "skuId");
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered2);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(BackOrderedRedTreeEntity.UpdatedSubBranchEvent.class);
        assertEquals(subBranchId, event.subBranchId());
        assertEquals(parentId, event.parentId());
        assertEquals(subBranch, event.subBranch());

        var subBranches = event.subBranches();
        var quantityBackOrderedEvent = subBranches.stream()
            .mapToInt(BackOrderedRedTreeEntity.SubBranch::quantityBackOrdered)
            .sum();
        assertEquals(quantityBackOrdered1 + quantityBackOrdered2, quantityBackOrderedEvent);
      }
    }

    {
      var state = testKit.getState();
      var subBranches = state.subBranches();
      var quantityBackOrderedState = subBranches.stream()
          .mapToInt(BackOrderedRedTreeEntity.SubBranch::quantityBackOrdered)
          .sum();
      assertEquals(quantityBackOrdered1 + quantityBackOrdered2, quantityBackOrderedState);
    }
  }

  @Test
  void updateThreeSubBranchesRemoveOneTest() {
    var testKit = EventSourcedTestKit.of(BackOrderedRedTreeEntity::new);

    var quantityBackOrdered1 = 12;
    var quantityBackOrdered2 = 23;
    var quantityBackOrdered3 = 34;
    var orderItemRedLeafId2 = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId-2", "skuId");
    var parentId = BackOrderedRedTreeEntity.BackOrderedRedTreeId
        .of(OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId", "skuId"))
        .levelDown();

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId-1", "skuId");
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered1);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(2, result.getAllEvents().size());
    }

    {
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId2);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered2);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId-3", "skuId");
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered3);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var state = testKit.getState();
      var subBranches = state.subBranches();
      var quantityBackOrderedState = subBranches.stream()
          .mapToInt(BackOrderedRedTreeEntity.SubBranch::quantityBackOrdered)
          .sum();
      assertEquals(quantityBackOrdered1 + quantityBackOrdered2 + quantityBackOrdered3, quantityBackOrderedState);
      assertEquals(3, state.subBranches().size());
    }

    {
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId2);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, 0);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var state = testKit.getState();
      var subBranches = state.subBranches();
      var quantityBackOrderedState = subBranches.stream()
          .mapToInt(BackOrderedRedTreeEntity.SubBranch::quantityBackOrdered)
          .sum();
      assertEquals(quantityBackOrdered1 + quantityBackOrdered3, quantityBackOrderedState);
      assertEquals(2, state.subBranches().size());
    }
  }

  @Test
  void updateThreeSubBranchesThenReleaseToParentTest() {
    var testKit = EventSourcedTestKit.of(BackOrderedRedTreeEntity::new);

    var quantityBackOrdered1 = 12;
    var quantityBackOrdered2 = 23;
    var quantityBackOrdered3 = 34;
    var parentId = BackOrderedRedTreeEntity.BackOrderedRedTreeId
        .of(OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId", "skuId"))
        .levelDown();

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId-1", "skuId");
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered1);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(2, result.getAllEvents().size());
    }

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId-2", "skuId");
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered2);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderId-3", "skuId");
      var subBranchId = BackOrderedRedTreeEntity.BackOrderedRedTreeId.of(orderItemRedLeafId);
      var subBranch = new BackOrderedRedTreeEntity.SubBranch(subBranchId, quantityBackOrdered3);
      var command = new BackOrderedRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var command = new BackOrderedRedTreeEntity.ReleaseToParentCommand(parentId, parentId.levelDown());
      var result = testKit.call(e -> e.releaseToParent(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(BackOrderedRedTreeEntity.ReleasedToParentEvent.class);
      assertEquals(parentId.levelDown(), event.parentId());
      assertEquals(parentId, event.subBranch().backOrderedRedTreeId());
      assertEquals(quantityBackOrdered1 + quantityBackOrdered2 + quantityBackOrdered3, event.subBranch().quantityBackOrdered());
    }
  }
}
