package io.example.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.example.shipping.OrderItemRedLeafEntity.Event;
import io.example.shipping.OrderItemRedLeafEntity.State;
import io.example.stock.StockOrderRedLeafEntity;
import kalix.javasdk.testkit.EventSourcedTestKit;

class OrderItemRedLeafEntityTest {
  static final int quantityLeavesPerTree = 128;
  static final int quantityLeavesPerBranch = 32;

  @Test
  void createTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
      var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");
      var quantity = 21;
      var command = new OrderItemRedLeafEntity.OrderItemCreateCommand(orderItemRedLeafId, parentId, quantity);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      {
        var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemCreatedEvent.class);
        assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
        assertEquals(quantity, event.quantity());
        assertFalse(event.orderSkuItemsAvailable().isEmpty());
        assertEquals(quantity, event.orderSkuItemsAvailable().size());
      }

      {
        var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
        assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
        assertFalse(event.orderSkuItemIds().isEmpty());
        assertEquals(quantity, event.orderSkuItemIds().size());
      }

      var state = testKit.getState();
      assertEquals(orderItemRedLeafId, state.orderItemRedLeafId());
      assertEquals(quantity, state.quantity());
      assertNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
      assertFalse(state.orderSkuItemsAvailable().isEmpty());
      assertEquals(quantity, state.orderSkuItemsAvailable().size());
    }
  }

  @Test
  void getTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
      var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");
      var quantity = 21;
      var command = new OrderItemRedLeafEntity.OrderItemCreateCommand(orderItemRedLeafId, parentId, quantity);
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
  void singleOrderItemConsumesStockSkuItemsTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
    var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");

    var quantityOrderItem = 17;
    var quantityRequested = 10;

    {
      var command = new OrderItemRedLeafEntity.OrderItemCreateCommand(orderItemRedLeafId, parentId, quantityOrderItem);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // first request gets no orderSkuItems because the stockOrder is not yet available to be consumed
      var stockOrderRedLeafId = stockOrderRedLeafIdOf("stockOrderId-1", "skuId");
      var stockSkuItemIds = IntStream.range(0, quantityRequested)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, stockSkuItemIds);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(0, event.orderSkuItemsConsumed().size());
    }

    { // set available the stockOrder, which is setting the orderSkuItems into a back ordered state
      var command = new OrderItemRedLeafEntity.OrderItemSetBackOrderedCommand(orderItemRedLeafId);
      var result = testKit.call(e -> e.orderItemSetBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // second request gets the orderSkuItems because the stockOrder is now available to be consumed
      var stockOrderRedLeafId = stockOrderRedLeafIdOf("stockOrderId-1", "skuId");
      var stockSkuItemIds = IntStream.range(0, quantityRequested)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, stockSkuItemIds);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(1, event.orderSkuItemsConsumed().size());
      assertEquals(quantityRequested, event.orderSkuItemsConsumed().get(0).orderSkuItemsToStockSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(quantityOrderItem - quantityRequested, state.orderSkuItemsAvailable().size());
      assertNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
    }
  }

  EventSourcedTestKit<State, Event, OrderItemRedLeafEntity> createOrderItemRedLeafAndSetToBackOrdered(
      OrderItemRedLeafEntity.OrderItemRedLeafId orderItemRedLeafId, OrderItemRedTreeEntity.OrderItemRedTreeId parentId,
      int quantityOrderItem, boolean makeAvailableToConsume) {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);

    {
      var command = new OrderItemRedLeafEntity.OrderItemCreateCommand(orderItemRedLeafId, parentId, quantityOrderItem);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    if (makeAvailableToConsume) {
      var command = new OrderItemRedLeafEntity.OrderItemSetBackOrderedCommand(orderItemRedLeafId);
      var result = testKit.call(e -> e.orderItemSetBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    return testKit;
  }

  @Test
  void singleOrderItemConsumesStockSkuItemsIdempotentTest() {
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
    var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");
    var quantityOrderItem = 17;
    var quantityRequested = 10;

    var testKit = createOrderItemRedLeafAndSetToBackOrdered(orderItemRedLeafId, parentId, quantityOrderItem, true);

    var stockOrderRedLeafId = stockOrderRedLeafIdOf("stockOrderId", "skuId");
    var stockSkuItems = IntStream.range(0, quantityRequested)
        .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId))
        .toList();

    { // first request
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, stockSkuItems);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(1, event.orderSkuItemsConsumed().size());
      assertEquals(quantityRequested, event.orderSkuItemsConsumed().get(0).orderSkuItemsToStockSkuItems().size());
    }

    { // set to back ordered
      var command = new OrderItemRedLeafEntity.OrderItemSetBackOrderedCommand(orderItemRedLeafId);
      var result = testKit.call(e -> e.orderItemSetBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // second request
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, stockSkuItems);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(1, eventCount);

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(1, event.orderSkuItemsConsumed().size());
      assertEquals(quantityRequested, event.orderSkuItemsConsumed().get(0).orderSkuItemsToStockSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(quantityOrderItem - quantityRequested, state.orderSkuItemsAvailable().size());
    }
  }

  @Test
  void multipleStockOrdersConsumeOrderSkuItemsTest() {
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
    var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");

    var quantityOrderItem = 17;
    var quantityRequested1 = 10;
    var quantityRequested2 = 11;
    var quantityRequested3 = 12;

    var testKit = createOrderItemRedLeafAndSetToBackOrdered(orderItemRedLeafId, parentId, quantityOrderItem, true);

    { // this stock order request will be fully consumed
      var stockOrderRedLeafId1 = stockOrderRedLeafIdOf("orderItemId-1", "skuId");
      var stockSkuItemIds1 = IntStream.range(0, quantityRequested1)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId1))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId1, stockSkuItemIds1);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId1, event.stockOrderRedLeafId());
      assertEquals(1, event.orderSkuItemsConsumed().size());
      assertEquals(quantityRequested1, event.orderSkuItemsConsumed().get(0).orderSkuItemsToStockSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(quantityOrderItem - quantityRequested1, state.orderSkuItemsAvailable().size());
      assertNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
    }

    { // set to back ordered
      var command = new OrderItemRedLeafEntity.OrderItemSetBackOrderedCommand(orderItemRedLeafId);
      var result = testKit.call(e -> e.orderItemSetBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // this stock order request will be partially consumed
      var stockOrderRedLeafId2 = stockOrderRedLeafIdOf("orderItemId-2", "skuId");
      var stockSkuItemIds2 = IntStream.range(0, quantityRequested2)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId2))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId2, stockSkuItemIds2);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId2, event.stockOrderRedLeafId());
      var quantityExpected = Math.max(0, quantityOrderItem - quantityRequested1);
      assertEquals(2, event.orderSkuItemsConsumed().size());
      assertEquals(quantityExpected, event.orderSkuItemsConsumed().get(1).orderSkuItemsToStockSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(0, state.orderSkuItemsAvailable().size());
      assertNotNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
    }

    { // this order will not be consumed
      var stockOrderRedLeafId2 = stockOrderRedLeafIdOf("orderItemId-3", "skuId");
      var stockSkuItemIds2 = IntStream.range(0, quantityRequested3)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId2))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId2, stockSkuItemIds2);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId2, event.stockOrderRedLeafId());
    }

    {
      var state = testKit.getState();
      assertEquals(0, state.orderSkuItemsAvailable().size());
      assertNotNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
    }
  }

  @Test
  void multipleStockOrdersConsumeOrderSkuItemsAndReleaseTest() {
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
    var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");
    var stockOrderRedLeafIdRelease = stockOrderRedLeafIdOf("orderItemId-2", "skuId");

    var quantityOrderItem = 17;
    var quantityRequested1 = 10;
    var quantityRequested2 = 11;
    var quantityRequested3 = 12;

    var testKit = createOrderItemRedLeafAndSetToBackOrdered(orderItemRedLeafId, parentId, quantityOrderItem, true);

    { // this stock order request will be fully consumed
      var stockOrderRedLeafId = stockOrderRedLeafIdOf("orderItemId-1", "skuId");
      var stockSkuItemIds1 = IntStream.range(0, quantityRequested1)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, stockSkuItemIds1);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(3, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
        assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
        assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
        assertEquals(1, event.orderSkuItemsConsumed().size());
        assertEquals(quantityRequested1, event.orderSkuItemsConsumed().get(0).orderSkuItemsToStockSkuItems().size());
      }

      {
        var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemSetBackOrderedOffEvent.class);
        assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      }

      {
        var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
        assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
        assertEquals(quantityOrderItem - quantityRequested1, event.orderSkuItemIds().size());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(quantityOrderItem - quantityRequested1, state.orderSkuItemsAvailable().size());
      assertNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
    }

    { // set to back ordered
      var command = new OrderItemRedLeafEntity.OrderItemSetBackOrderedCommand(orderItemRedLeafId);
      var result = testKit.call(e -> e.orderItemSetBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // this stock order request will be partially consumed
      var stockSkuItemIds2 = IntStream.range(0, quantityRequested2)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafIdRelease))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafIdRelease, stockSkuItemIds2);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafIdRelease, event.stockOrderRedLeafId());
      assertEquals(2, event.orderSkuItemsConsumed().size());
      assertEquals(quantityOrderItem - quantityRequested1, event.consumed().orderSkuItemsToStockSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertNotNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
      assertEquals(0, state.orderSkuItemsAvailable().size());
    }

    { // release order-2 orderSkuItems
      var command = new OrderItemRedLeafEntity.StockOrderReleaseOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafIdRelease);
      var result = testKit.call(e -> e.stockOrderReleaseOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var state = testKit.getState();
      assertNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
      assertEquals(quantityOrderItem - quantityRequested1, state.orderSkuItemsAvailable().size());
    }

    { // this stock order request will not be consumed because the order item is no longer in a back ordered state
      var stockOrderRedLeafId2 = stockOrderRedLeafIdOf("orderItemId-3", "skuId");
      var stockSkuItemIds2 = IntStream.range(0, quantityRequested3)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId2))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId2, stockSkuItemIds2);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId2, event.stockOrderRedLeafId());
    }
  }

  @Test
  void consumeStockSkuItemsToOrderItemTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
    var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");

    var quantityOrderItem = 17;
    var quantityStockSkuItems = 10;

    {
      var command = new OrderItemRedLeafEntity.OrderItemCreateCommand(orderItemRedLeafId, parentId, quantityOrderItem);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    var state = testKit.getState();
    var stockOrderRedLeafId = stockOrderRedLeafIdOf("stockOrderId-1", "skuId");
    var orderSkuItems = state.orderSkuItemsAvailable().stream()
        .map(orderSkuItem -> new OrderItemRedLeafEntity.OrderSkuItemToStockSkuItem(orderSkuItem,
            StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId)))
        .limit(quantityStockSkuItems)
        .toList();

    {
      var command = new OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemConsumedStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(1, event.orderSkuItemsConsumed().size());
      assertEquals(quantityStockSkuItems, event.orderSkuItemsConsumed().get(0).orderSkuItemsToStockSkuItems().size());

      var eventRequest = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
      assertEquals(quantityOrderItem - quantityStockSkuItems, eventRequest.orderSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityRemaining = stateAfter.orderSkuItemsAvailable().size();
      assertEquals(quantityOrderItem - quantityStockSkuItems, quantityRemaining);
    }

    { // idempotent test
      var command = new OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemConsumedStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(0, eventCount);
    }
  }

  @Test
  void consumeStockSkuItemsForTwoOrderItemsTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
    var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");

    var quantityOrderItem = 17;
    var quantityStockSkuItems1 = 9;
    var quantityStockSkuItems2 = 8;

    {
      var command = new OrderItemRedLeafEntity.OrderItemCreateCommand(orderItemRedLeafId, parentId, quantityOrderItem);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // first stock order
      var state = testKit.getState();
      var stockOrderRedLeafId1 = stockOrderRedLeafIdOf("stockOrderId-1", "skuId");
      var orderSkuItems1 = state.orderSkuItemsAvailable().stream()
          .map(orderSkuItem -> new OrderItemRedLeafEntity.OrderSkuItemToStockSkuItem(orderSkuItem,
              StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId1)))
          .limit(quantityStockSkuItems1)
          .toList();

      var command = new OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId1, orderSkuItems1);
      var result = testKit.call(e -> e.orderItemConsumedStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount);

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId1, event.stockOrderRedLeafId());
      assertEquals(1, event.orderSkuItemsConsumed().size());
      assertEquals(quantityStockSkuItems1, event.orderSkuItemsConsumed().get(0).orderSkuItemsToStockSkuItems().size());

      var eventRequest = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
      assertEquals(quantityOrderItem - quantityStockSkuItems1, eventRequest.orderSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityRemaining = stateAfter.orderSkuItemsAvailable().size();
      assertEquals(quantityOrderItem - quantityStockSkuItems1, quantityRemaining);
    }

    { // second stock order
      var state = testKit.getState();
      var stockOrderRedLeafId2 = stockOrderRedLeafIdOf("stockOrderId-2", "skuId");
      var orderSkuItems2 = state.orderSkuItemsAvailable().stream()
          .map(orderSkuItem -> new OrderItemRedLeafEntity.OrderSkuItemToStockSkuItem(orderSkuItem,
              StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId2)))
          .limit(quantityStockSkuItems2)
          .toList();

      var command = new OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId2, orderSkuItems2);
      var result = testKit.call(e -> e.orderItemConsumedStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(1, eventCount); // only one event because the order is fully consumed and no request event is emitted

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId2, event.stockOrderRedLeafId());
      assertEquals(2, event.orderSkuItemsConsumed().size());
      assertEquals(quantityStockSkuItems2, event.orderSkuItemsConsumed().get(1).orderSkuItemsToStockSkuItems().size());

      var stateAfter = testKit.getState();
      var quantityRemaining = stateAfter.orderSkuItemsAvailable().size();
      assertEquals(quantityOrderItem - quantityStockSkuItems1 - quantityStockSkuItems2, quantityRemaining);
    }
  }

  @Test
  void consumeStockSkuItemsToOrderItemFromTwoStockOrdersOneReleasedTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.genId("orderItemId", "skuId");
    var parentId = OrderItemRedTreeEntity.OrderItemRedTreeId.genId("orderItemId2", "skuId");

    var quantityOrderItem = 17;
    var quantityStockSkuItems1 = 4;
    var quantityStockSkuItems2 = 13;

    {
      var command = new OrderItemRedLeafEntity.OrderItemCreateCommand(orderItemRedLeafId, parentId, quantityOrderItem);
      var result = testKit.call(e -> e.orderItemCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    // Assign the first stock order to the orderSkuItems
    var state = testKit.getState();
    var stockOrderRedLeafId1 = stockOrderRedLeafIdOf("stockOrderId-1", "skuId");
    var orderSkuItems1 = state.orderSkuItemsAvailable().stream()
        .map(orderSkuItem -> new OrderItemRedLeafEntity.OrderSkuItemToStockSkuItem(orderSkuItem,
            StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId1)))
        .limit(quantityStockSkuItems1)
        .toList();

    // Assign the second stock order to the overlapping orderSkuItems
    var stockOrderRedLeafId2 = stockOrderRedLeafIdOf("stockOrderId-2", "skuId");
    var orderSkuItems2 = state.orderSkuItemsAvailable().stream()
        .map(orderSkuItem -> new OrderItemRedLeafEntity.OrderSkuItemToStockSkuItem(orderSkuItem,
            StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId2)))
        .limit(quantityStockSkuItems2)
        .toList();

    { // first stock order
      var command = new OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId1, orderSkuItems1);
      var result = testKit.call(e -> e.orderItemConsumedStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount); // two events because the order is not fully consumed and a request event is emitted

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId1, event.stockOrderRedLeafId());
      assertEquals(1, event.orderSkuItemsConsumed().size());
      assertEquals(quantityStockSkuItems1, event.orderSkuItemsConsumed().get(0).orderSkuItemsToStockSkuItems().size());

      var eventRequest = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
      assertEquals(quantityOrderItem - quantityStockSkuItems1, eventRequest.orderSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityAvailable = stateAfter.orderSkuItemsAvailable().size();
      assertEquals(quantityOrderItem - quantityStockSkuItems1, quantityAvailable);
    }

    { // second stock order released because some of the orderSkuItems are already consumed
      var command = new OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId2, orderSkuItems2);
      var result = testKit.call(e -> e.orderItemConsumedStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount);

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemReleasedStockSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId2, event.stockOrderRedLeafId());

      var eventRequest = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
      assertEquals(quantityOrderItem - quantityStockSkuItems1, eventRequest.orderSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityConsumed = stateAfter.quantity() - stateAfter.orderSkuItemsAvailable().size();
      assertEquals(quantityStockSkuItems1, quantityConsumed);
    }
  }

  private static StockOrderRedLeafEntity.StockOrderRedLeafId stockOrderRedLeafIdOf(String stockOrderId, String skuId) {
    return StockOrderRedLeafEntity.StockOrderRedLeafId.genId(stockOrderId, skuId, quantityLeavesPerTree, quantityLeavesPerBranch);
  }
}
