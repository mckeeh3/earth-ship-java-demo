package io.example.stock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class StockOrderRedTreeEntityTest {
  @Test
  void updateOneSubBranch() {
    var testKit = EventSourcedTestKit.of(StockOrderRedTreeEntity::new);

    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("stockOrderId", "skuId", 100, 32);
    var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
    var parentId = subBranchId.levelDown();
    var quantityOrdered = 10;
    var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityOrdered);
    var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);

    {
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(StockOrderRedTreeEntity.UpdatedSubBranchEvent.class);
        assertEquals(parentId, event.parentId());
        assertEquals(subBranch, event.subBranch());

        var subBranches = event.subBranches();
        var quantityStockOrdered = subBranches.stream()
            .mapToInt(subBranch_ -> subBranch_.quantityStockOrder())
            .sum();
        assertEquals(quantityOrdered, quantityStockOrdered);
      }

      {
        var event = result.getNextEventOfType(StockOrderRedTreeEntity.UpdatedBranchEvent.class);
        assertEquals(parentId, event.stockOrderRedTreeId());
      }
    }

    var stateBefore = testKit.getState();

    {
      assertEquals(parentId, stateBefore.stockOrderRedTreeId());
      assertTrue(stateBefore.hasChanged());
      var quantityOrderedState = stateBefore.subBranches().stream()
          .mapToInt(subBranch_ -> subBranch_.quantityStockOrder())
          .sum();
      assertEquals(quantityOrdered, quantityOrderedState);
    }

    { // Idempotent test
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(StockOrderRedTreeEntity.UpdatedSubBranchEvent.class);
        assertEquals(subBranchId, event.subBranchId());
        assertEquals(parentId, event.parentId());
        assertEquals(subBranch, event.subBranch());

        var subBranches = event.subBranches();
        var quantityStockOrdered = subBranches.stream()
            .mapToInt(subBranch_ -> subBranch_.quantityStockOrder())
            .sum();
        assertEquals(quantityOrdered, quantityStockOrdered);
      }

      var stateAfter = testKit.getState();
      assertEquals(stateBefore, stateAfter);
    }
  }

  @Test
  void updateTwoSubBranchesTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedTreeEntity::new);

    var quantityStockOrder1 = 12;
    var quantityStockOrder2 = 23;
    var parentId = StockOrderRedTreeEntity.StockOrderRedTreeId
        .of(StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId", "skuId", 100, 32))
        .levelDown();

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId-1", "skuId", 100, 32);
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityStockOrder1);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(StockOrderRedTreeEntity.UpdatedSubBranchEvent.class);
        assertEquals(subBranchId, event.subBranchId());
        assertEquals(parentId, event.parentId());
        assertEquals(subBranch, event.subBranch());

        var subBranches = event.subBranches();
        var quantityStockOrderEvent = subBranches.stream()
            .mapToInt(StockOrderRedTreeEntity.SubBranch::quantityStockOrder)
            .sum();
        assertEquals(quantityStockOrder1, quantityStockOrderEvent);
      }

      {
        var event = result.getNextEventOfType(StockOrderRedTreeEntity.UpdatedBranchEvent.class);
        assertEquals(parentId, event.stockOrderRedTreeId());
      }
    }

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId-2", "skuId", 100, 32);
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityStockOrder2);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(StockOrderRedTreeEntity.UpdatedSubBranchEvent.class);
        assertEquals(subBranchId, event.subBranchId());
        assertEquals(parentId, event.parentId());
        assertEquals(subBranch, event.subBranch());

        var subBranches = event.subBranches();
        var quantityStockOrderEvent = subBranches.stream()
            .mapToInt(StockOrderRedTreeEntity.SubBranch::quantityStockOrder)
            .sum();
        assertEquals(quantityStockOrder1 + quantityStockOrder2, quantityStockOrderEvent);
      }
    }

    {
      var state = testKit.getState();
      var subBranches = state.subBranches();
      var quantityStockOrderState = subBranches.stream()
          .mapToInt(StockOrderRedTreeEntity.SubBranch::quantityStockOrder)
          .sum();
      assertEquals(quantityStockOrder1 + quantityStockOrder2, quantityStockOrderState);
    }
  }

  @Test
  void updateThreeSubBranchesRemoveOneTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedTreeEntity::new);

    var quantityStockOrder1 = 12;
    var quantityStockOrder2 = 23;
    var quantityStockOrder3 = 34;
    var stockOrderRedLeafId2 = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId-2", "skuId", 100, 32);
    var parentId = StockOrderRedTreeEntity.StockOrderRedTreeId
        .of(StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId", "skuId", 100, 32))
        .levelDown();

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId-1", "skuId", 100, 32);
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityStockOrder1);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(2, result.getAllEvents().size());
    }

    {
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId2);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityStockOrder2);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId-3", "skuId", 100, 32);
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityStockOrder3);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var state = testKit.getState();
      var subBranches = state.subBranches();
      var quantityStockOrderState = subBranches.stream()
          .mapToInt(StockOrderRedTreeEntity.SubBranch::quantityStockOrder)
          .sum();
      assertEquals(quantityStockOrder1 + quantityStockOrder2 + quantityStockOrder3, quantityStockOrderState);
      assertEquals(3, state.subBranches().size());
    }

    {
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId2);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, 0);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var state = testKit.getState();
      var subBranches = state.subBranches();
      var quantityStockOrderState = subBranches.stream()
          .mapToInt(StockOrderRedTreeEntity.SubBranch::quantityStockOrder)
          .sum();
      assertEquals(quantityStockOrder1 + quantityStockOrder3, quantityStockOrderState);
      assertEquals(2, state.subBranches().size());
    }
  }

  @Test
  void updateThreeSubBranchesThenReleaseToParentTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedTreeEntity::new);

    var quantityStockOrder1 = 12;
    var quantityStockOrder2 = 23;
    var quantityStockOrder3 = 34;
    var parentId = StockOrderRedTreeEntity.StockOrderRedTreeId
        .of(StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId", "skuId", 100, 32))
        .levelDown();

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId-1", "skuId", 100, 32);
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityStockOrder1);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(2, result.getAllEvents().size());
    }

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId-2", "skuId", 100, 32);
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityStockOrder2);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.genId("orderId-3", "skuId", 100, 32);
      var subBranchId = StockOrderRedTreeEntity.StockOrderRedTreeId.of(stockOrderRedLeafId);
      var subBranch = new StockOrderRedTreeEntity.SubBranch(subBranchId, quantityStockOrder3);
      var command = new StockOrderRedTreeEntity.UpdateSubBranchCommand(subBranchId, parentId, subBranch);
      var result = testKit.call(e -> e.updateSubBranch(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var command = new StockOrderRedTreeEntity.ReleaseToParentCommand(parentId, parentId.levelDown());
      var result = testKit.call(e -> e.releaseToParent(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(StockOrderRedTreeEntity.ReleasedToParentEvent.class);
      assertEquals(parentId.levelDown(), event.parentId());
      assertEquals(parentId, event.subBranch().stockOrderRedTreeId());
      assertEquals(quantityStockOrder1 + quantityStockOrder2 + quantityStockOrder3, event.subBranch().quantityStockOrder());
    }
  }
}
