package io.example.stock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

class StockOrderRedTreeEntityTest {
  @Test
  void stockOrderCreateTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedTreeEntity::new);
    var stockOrderRedTreeId = StockOrderRedTreeEntity.StockOrderRedTreeId.of("orderId", "skuId");
    var quantity = 1247;

    {
      var command = new StockOrderRedTreeEntity.StockOrderRedTreeCreateCommand(stockOrderRedTreeId, null, quantity);
      var result = testKit.call(e -> e.stockOrderCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedTreeEntity.StockOrderRedTreeCreatedEvent.class);
      assertEquals(stockOrderRedTreeId, event.stockOrderRedTreeId());
      assertNull(event.parentId());
      assertEquals(quantity, event.quantity());
      assertTrue(event.subBranches().size() > 0);

      var reduced = StockOrderRedTreeEntity.SubBranch.reduce(event.subBranches());
      assertEquals(quantity, reduced.quantity());
    }

    {
      var state = testKit.getState();
      assertEquals(stockOrderRedTreeId, state.stockOrderRedTreeId());
      assertNull(state.parentId());
      assertEquals(quantity, state.quantity());
      assertEquals(0, state.quantityOrdered());
      assertTrue(state.subBranches().size() > 0);
    }
  }

  @Test
  void getTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedTreeEntity::new);
    var stockOrderRedTreeId = StockOrderRedTreeEntity.StockOrderRedTreeId.of("orderId", "skuId");
    var quantity = 1247;

    {
      var command = new StockOrderRedTreeEntity.StockOrderRedTreeCreateCommand(stockOrderRedTreeId, null, quantity);
      var result = testKit.call(e -> e.stockOrderCreate(command));
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
    var testKit = EventSourcedTestKit.of(StockOrderRedTreeEntity::new);
    var stockOrderRedTreeId = StockOrderRedTreeEntity.StockOrderRedTreeId.of("orderId", "skuId");

    var quantity = 1247;
    var quantityOrdered = 12;

    {
      var command = new StockOrderRedTreeEntity.StockOrderRedTreeCreateCommand(stockOrderRedTreeId, null, quantity);
      var result = testKit.call(e -> e.stockOrderCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var subBranch = testKit.getState().subBranches().get(0);
      var command = new StockOrderRedTreeEntity.StockOrderSubBranchUpdateCommand(
          subBranch.stockOrderRedTreeId(),
          subBranch.parentId(),
          subBranch.quantity(),
          quantityOrdered);

      var result = testKit.call(e -> e.stockOrderSubBranchUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(1, eventCount);

      var event = result.getNextEventOfType(StockOrderRedTreeEntity.StockOrderSubBranchUpdatedEvent.class);
      assertEquals(subBranch.stockOrderRedTreeId(), event.stockOrderRedTreeId());
      assertEquals(stockOrderRedTreeId, event.parentId());
      assertEquals(subBranch.quantity(), event.quantity());
      assertEquals(quantityOrdered, event.quantityOrdered());
    }

    {
      var state = testKit.getState();
      assertEquals(stockOrderRedTreeId, state.stockOrderRedTreeId());
      assertNull(state.parentId());
      assertEquals(quantity, state.quantity());
      assertEquals(quantityOrdered, state.quantityOrdered());
      assertTrue(state.subBranches().size() > 0);

      var reduced = StockOrderRedTreeEntity.SubBranch.reduce(state.subBranches());
      assertEquals(quantityOrdered, reduced.quantityOrdered());
    }
  }

  @Test
  void updateTwoSubBranchesOnNonTrunkBranchTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedTreeEntity::new);
    var stockOrderRedTreeId = StockOrderRedTreeEntity.StockOrderRedTreeId.of("orderId", "skuId");
    var parentId = StockOrderRedTreeEntity.StockOrderRedTreeId.of("parent-orderId", "skuId");

    var quantity = 1247;
    var quantityOrdered1 = 12;
    var quantityOrdered2 = 23;

    {
      var command = new StockOrderRedTreeEntity.StockOrderRedTreeCreateCommand(stockOrderRedTreeId, parentId, quantity);
      var result = testKit.call(e -> e.stockOrderCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var subBranchIdx = 0;
      var subBranch = testKit.getState().subBranches().get(subBranchIdx);
      var command = new StockOrderRedTreeEntity.StockOrderSubBranchUpdateCommand(
          subBranch.stockOrderRedTreeId(),
          subBranch.parentId(),
          subBranch.quantity(),
          quantityOrdered1);

      var result = testKit.call(e -> e.stockOrderSubBranchUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount);

      var event = result.getNextEventOfType(StockOrderRedTreeEntity.StockOrderSubBranchUpdatedEvent.class);
      assertEquals(subBranch.stockOrderRedTreeId(), event.stockOrderRedTreeId());
      assertEquals(stockOrderRedTreeId, event.parentId());
      assertEquals(subBranch.quantity(), event.quantity());
      assertEquals(quantityOrdered1, event.quantityOrdered());

      var parentEvent = result.getNextEventOfType(StockOrderRedTreeEntity.StockOrderSubBranchParentUpdatedEvent.class);
      assertEquals(stockOrderRedTreeId, parentEvent.stockOrderRedTreeId());
      assertEquals(parentId, parentEvent.parentId());
      assertEquals(quantity, parentEvent.quantity());
      assertEquals(quantityOrdered1, parentEvent.quantityOrdered());
    }

    {
      var subBranchIdx = 1;
      var subBranch = testKit.getState().subBranches().get(subBranchIdx);
      var command = new StockOrderRedTreeEntity.StockOrderSubBranchUpdateCommand(
          subBranch.stockOrderRedTreeId(),
          subBranch.parentId(),
          subBranch.quantity(),
          quantityOrdered2);

      var result = testKit.call(e -> e.stockOrderSubBranchUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount);

      var event = result.getNextEventOfType(StockOrderRedTreeEntity.StockOrderSubBranchUpdatedEvent.class);
      assertEquals(subBranch.stockOrderRedTreeId(), event.stockOrderRedTreeId());
      assertEquals(stockOrderRedTreeId, event.parentId());
      assertEquals(subBranch.quantity(), event.quantity());
      assertEquals(quantityOrdered2, event.quantityOrdered());

      var parentEvent = result.getNextEventOfType(StockOrderRedTreeEntity.StockOrderSubBranchParentUpdatedEvent.class);
      assertEquals(stockOrderRedTreeId, parentEvent.stockOrderRedTreeId());
      assertEquals(parentId, parentEvent.parentId());
      assertEquals(quantity, parentEvent.quantity());
      assertEquals(quantityOrdered1 + quantityOrdered2, parentEvent.quantityOrdered());
    }
  }

  @Test
  void subBranchTest() {
    var stockOrderRedTreeId = StockOrderRedTreeEntity.StockOrderRedTreeId.of("orderId", "skuId");

    var quantity = 1247;
    var sUbBranches = StockOrderRedTreeEntity.SubBranch.subBranchesOf(stockOrderRedTreeId, quantity);
    var reduced = StockOrderRedTreeEntity.SubBranch.reduce(sUbBranches);
    assertEquals(quantity, reduced.quantity());
  }

  @Test
  void testSubBranchCreation() {
    testSubBranchCreation(21);
    testSubBranchCreation(9);
    testSubBranchCreation(10);
    testSubBranchCreation(13);
    testSubBranchCreation(17);
    testSubBranchCreation(19);
    testSubBranchCreation(20);
    testSubBranchCreation(21);
    testSubBranchCreation(22);
    testSubBranchCreation(29);
    testSubBranchCreation(39);
    testSubBranchCreation(40);
    testSubBranchCreation(41);
    testSubBranchCreation(176);
    testSubBranchCreation(199);
    testSubBranchCreation(1247);
    testSubBranchCreation(125);
    testSubBranchCreation(122);

    var subBranch = testSubBranchCreation(12479);
    while (subBranch.isPresent()) {
      subBranch = testSubBranchCreation(subBranch.get().quantity());
    }
  }

  Optional<StockOrderRedTreeEntity.SubBranch> testSubBranchCreation(int quantity) {
    var stockOrderRedTreeId = StockOrderRedTreeEntity.StockOrderRedTreeId.of("orderId", "skuId");
    var subBranches = StockOrderRedTreeEntity.SubBranch.subBranchesOf(stockOrderRedTreeId, quantity);
    var reduced = StockOrderRedTreeEntity.SubBranch.reduce(subBranches);

    assertEquals(quantity, reduced.quantity());
    assertTrue(quantity <= StockOrderRedTreeEntity.SubBranch.maxLeavesPerBranch && subBranches.size() == 1
        || quantity > StockOrderRedTreeEntity.SubBranch.maxLeavesPerBranch && subBranches.size() > 1);

    return quantity <= StockOrderRedTreeEntity.SubBranch.maxLeavesPerBranch
        ? Optional.empty()
        : Optional.of(subBranches.get(0));
  }
}
