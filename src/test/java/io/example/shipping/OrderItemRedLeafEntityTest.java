package io.example.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.example.shipping.OrderItemRedLeafEntity.Event;
import io.example.shipping.OrderItemRedLeafEntity.State;
import io.example.stock.StockOrderRedLeafEntity;
import kalix.javasdk.testkit.EventSourcedTestKit;

class OrderItemRedLeafEntityTest {
  @Test
  void createTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");
      var quantity = 21;
      var command = new OrderItemRedLeafEntity.OrderItemRedLeafCreateCommand(orderItemRedLeafId, quantity);
      var result = testKit.call(e -> e.createOrderItemRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      {
        var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRedLeafCreatedEvent.class);
        assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
        assertEquals(quantity, event.quantity());
        assertFalse(event.orderSkuItemIds().isEmpty());
        assertEquals(quantity, event.orderSkuItemIds().size());
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
      assertFalse(state.orderSkuItemIds().isEmpty());
      assertEquals(quantity, state.orderSkuItemIds().size());
    }
  }

  @Test
  void getTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);

    {
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");
      var quantity = 21;
      var command = new OrderItemRedLeafEntity.OrderItemRedLeafCreateCommand(orderItemRedLeafId, quantity);
      var result = testKit.call(e -> e.createOrderItemRedLeaf(command));
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
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");

    var quantityOrderItem = 17;
    var quantityRequested = 10;

    {
      var command = new OrderItemRedLeafEntity.OrderItemRedLeafCreateCommand(orderItemRedLeafId, quantityOrderItem);
      var result = testKit.call(e -> e.createOrderItemRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // first request gets no orderSkuItems because the stockOrder is not yet available to be consumed
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-1", "skuId");
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
      assertEquals(0, event.orderSkuItemToStockSkuItems().size());
    }

    { // set available the stockOrder, which is setting the orderSkuItems into a back ordered state
      var command = new OrderItemRedLeafEntity.OrderItemSetBackOrderedCommand(orderItemRedLeafId);
      var result = testKit.call(e -> e.orderItemSetBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // second request gets the orderSkuItems because the stockOrder is now available to be consumed
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-1", "skuId");
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
      assertEquals(quantityRequested, event.orderSkuItemToStockSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(quantityOrderItem - quantityRequested, state.orderSkuItemIds().size());
    }
  }

  EventSourcedTestKit<State, Event, OrderItemRedLeafEntity> createOrderItemRedLeaf(OrderItemRedLeafEntity.OrderItemRedLeafId orderItemRedLeafId,
      int quantityOrderItem, boolean makeAvailableToConsume) {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);

    {
      var command = new OrderItemRedLeafEntity.OrderItemRedLeafCreateCommand(orderItemRedLeafId, quantityOrderItem);
      var result = testKit.call(e -> e.createOrderItemRedLeaf(command));
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
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");
    var quantityOrderItem = 17;
    var quantityRequested = 10;

    var testKit = createOrderItemRedLeaf(orderItemRedLeafId, quantityOrderItem, true);

    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId", "skuId");
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
      assertEquals(quantityRequested, event.orderSkuItemToStockSkuItems().size());
    }

    { // second request
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, stockSkuItems);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(0, eventCount);
    }

    {
      var state = testKit.getState();
      assertEquals(quantityOrderItem - quantityRequested, state.orderSkuItemIds().size());
    }
  }

  @Test
  void allocateOrderSkuItemsToMultipleStockOrdersTest() {
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");

    var quantityOrderItem = 17;
    var quantityRequested1 = 10;
    var quantityRequested2 = 11;
    var quantityRequested3 = 12;

    var testKit = createOrderItemRedLeaf(orderItemRedLeafId, quantityOrderItem, true);

    { // this order will be fully allocated
      var stockOrderRedLeafId1 = StockOrderRedLeafEntity.StockOrderRedLeafId.of("orderItemId-1", "skuId");
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
      assertEquals(quantityRequested1, event.orderSkuItemToStockSkuItems().size());
    }

    { // this order will be partially allocated
      var stockOrderRedLeafId2 = StockOrderRedLeafEntity.StockOrderRedLeafId.of("orderItemId-2", "skuId");
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
      assertEquals(quantityExpected, event.orderSkuItemToStockSkuItems().size());
    }

    { // this order will not be allocated
      var stockOrderRedLeafId2 = StockOrderRedLeafEntity.StockOrderRedLeafId.of("orderItemId-3", "skuId");
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
      var quantityExpected = Math.max(0, quantityOrderItem - quantityRequested1 - quantityRequested2);
      assertEquals(quantityExpected, event.orderSkuItemToStockSkuItems().size());
    }
  }

  @Test
  void allocateOrderSkuItemsToMultipleStockOrdersAndReleaseTest() {
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");

    var quantityOrderItem = 17;
    var quantityRequested1 = 10;
    var quantityRequested2 = 11;
    var quantityRequested3 = 12;

    var testKit = createOrderItemRedLeaf(orderItemRedLeafId, quantityOrderItem, true);

    { // this order item will be fully allocated
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("orderItemId-1", "skuId");
      var stockSkuItemIds1 = IntStream.range(0, quantityRequested1)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, stockSkuItemIds1);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(quantityRequested1, event.orderSkuItemToStockSkuItems().size());
    }

    { // this order item will be partially allocated
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("orderItemId-2", "skuId");
      var stockSkuItemIds2 = IntStream.range(0, quantityRequested2)
          .mapToObj(i -> StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId))
          .toList();
      var command = new OrderItemRedLeafEntity.StockOrderRequestsOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId, stockSkuItemIds2);
      var result = testKit.call(e -> e.stockOrderRequestsOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      var quantityExpected = Math.max(0, quantityOrderItem - quantityRequested1);
      assertEquals(quantityExpected, event.orderSkuItemToStockSkuItems().size());
    }

    { // release order-2 orderSkuItems
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("orderItemId-2", "skuId");
      var command = new OrderItemRedLeafEntity.StockOrderReleaseOrderSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId);
      var result = testKit.call(e -> e.stockOrderReleaseOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // this order will be partially allocated
      var stockOrderRedLeafId2 = StockOrderRedLeafEntity.StockOrderRedLeafId.of("orderItemId-3", "skuId");
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
      var quantityExpected = Math.max(0, quantityOrderItem - quantityRequested1);
      assertEquals(quantityExpected, event.orderSkuItemToStockSkuItems().size());
    }
  }

  @Test
  void consumeStockSkuItemsToOrderItemTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");

    var quantityOrderItem = 17;
    var quantityStockSkuItems = 10;

    {
      var command = new OrderItemRedLeafEntity.OrderItemRedLeafCreateCommand(orderItemRedLeafId, quantityOrderItem);
      var result = testKit.call(e -> e.createOrderItemRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    var state = testKit.getState();
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-1", "skuId");
    var orderSkuItems = state.orderSkuItemIds().stream()
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
      assertEquals(quantityStockSkuItems, event.orderSkuItemsToStockSckItems().size());

      var eventRequest = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
      assertEquals(quantityOrderItem - quantityStockSkuItems, eventRequest.orderSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityRemaining = stateAfter.orderSkuItemIds().size();
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
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");

    var quantityOrderItem = 17;
    var quantityStockSkuItems1 = 9;
    var quantityStockSkuItems2 = 8;

    {
      var command = new OrderItemRedLeafEntity.OrderItemRedLeafCreateCommand(orderItemRedLeafId, quantityOrderItem);
      var result = testKit.call(e -> e.createOrderItemRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // first stock order
      var state = testKit.getState();
      var stockOrderRedLeafId1 = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-1", "skuId");
      var orderSkuItems1 = state.orderSkuItemIds().stream()
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
      assertEquals(quantityStockSkuItems1, event.orderSkuItemsToStockSckItems().size());

      var eventRequest = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
      assertEquals(quantityOrderItem - quantityStockSkuItems1, eventRequest.orderSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityRemaining = stateAfter.orderSkuItemIds().size();
      assertEquals(quantityOrderItem - quantityStockSkuItems1, quantityRemaining);
    }

    { // second stock order
      var state = testKit.getState();
      var stockOrderRedLeafId2 = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-2", "skuId");
      var orderSkuItems2 = state.orderSkuItemIds().stream()
          .map(orderSkuItem -> new OrderItemRedLeafEntity.OrderSkuItemToStockSkuItem(orderSkuItem,
              StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId2)))
          .limit(quantityStockSkuItems2)
          .toList();

      var command = new OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsCommand(orderItemRedLeafId, stockOrderRedLeafId2, orderSkuItems2);
      var result = testKit.call(e -> e.orderItemConsumedStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(1, eventCount); // only one event because the order is fully allocated and no request event is emitted

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId2, event.stockOrderRedLeafId());
      assertEquals(quantityStockSkuItems2, event.orderSkuItemsToStockSckItems().size());

      var stateAfter = testKit.getState();
      var quantityRemaining = stateAfter.orderSkuItemIds().size();
      assertEquals(quantityOrderItem - quantityStockSkuItems1 - quantityStockSkuItems2, quantityRemaining);
    }
  }

  @Test
  void consumeStockSkuItemsToOrderItemFromTwoStockOrdersOneReleasedTest() {
    var testKit = EventSourcedTestKit.of(OrderItemRedLeafEntity::new);
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId", "skuId");

    var quantityOrderItem = 17;
    var quantityStockSkuItems1 = 4;
    var quantityStockSkuItems2 = 13;

    {
      var command = new OrderItemRedLeafEntity.OrderItemRedLeafCreateCommand(orderItemRedLeafId, quantityOrderItem);
      var result = testKit.call(e -> e.createOrderItemRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    // Assign the first stock order to the orderSkuItems
    var state = testKit.getState();
    var stockOrderRedLeafId1 = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-1", "skuId");
    var orderSkuItems1 = state.orderSkuItemIds().stream()
        .map(orderSkuItem -> new OrderItemRedLeafEntity.OrderSkuItemToStockSkuItem(orderSkuItem,
            StockOrderRedLeafEntity.StockSkuItemId.of(stockOrderRedLeafId1)))
        .limit(quantityStockSkuItems1)
        .toList();

    // Assign the second stock order to the overlapping orderSkuItems
    var stockOrderRedLeafId2 = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-2", "skuId");
    var orderSkuItems2 = state.orderSkuItemIds().stream()
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
      assertEquals(2, eventCount); // two events because the order is not fully allocated and a request event is emitted

      var event = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(stockOrderRedLeafId1, event.stockOrderRedLeafId());
      assertEquals(quantityStockSkuItems1, event.orderSkuItemsToStockSckItems().size());

      var eventRequest = result.getNextEventOfType(OrderItemRedLeafEntity.OrderItemRequestsStockSkuItemsEvent.class);
      assertEquals(quantityOrderItem - quantityStockSkuItems1, eventRequest.orderSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityAvailable = stateAfter.orderSkuItemIds().size();
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
      var quantityConsumed = stateAfter.quantity() - stateAfter.orderSkuItemIds().size();
      assertEquals(quantityStockSkuItems1, quantityConsumed);
    }
  }
}
