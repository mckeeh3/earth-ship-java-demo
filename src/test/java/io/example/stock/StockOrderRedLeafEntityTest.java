package io.example.stock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.example.shipping.OrderItemRedLeafEntity;
import io.example.stock.StockOrderRedLeafEntity.Event;
import io.example.stock.StockOrderRedLeafEntity.State;
import io.example.stock.StockOrderRedLeafEntity.StockOrderRedLeafId;
import kalix.javasdk.testkit.EventSourcedTestKit;

class StockOrderRedLeafEntityTest {
  @Test
  void createTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedLeafEntity::new);

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId", "skuId");
      var quantity = 21;
      var command = new StockOrderRedLeafEntity.StockOrderRedLeafCreateCommand(stockOrderRedLeafId, quantity);
      var result = testKit.call(e -> e.createStockOrderRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      {
        var event = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderRedLeafCreatedEvent.class);
        assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
        assertEquals(quantity, event.quantity());
        assertFalse(event.stockSkuItemsAvailable().isEmpty());
        assertEquals(quantity, event.stockSkuItemsAvailable().size());
      }

      {
        var event = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent.class);
        assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
        assertFalse(event.stockSkuItemIds().isEmpty());
        assertEquals(quantity, event.stockSkuItemIds().size());
      }

      var state = testKit.getState();
      assertEquals(stockOrderRedLeafId, state.stockOrderRedLeafId());
      assertEquals(quantity, state.quantity());
      assertFalse(state.stockSkuItemsAvailable().isEmpty());
      assertEquals(quantity, state.stockSkuItemsAvailable().size());
    }
  }

  @Test
  void getTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedLeafEntity::new);

    {
      var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId", "skuId");
      var quantity = 21;
      var command = new StockOrderRedLeafEntity.StockOrderRedLeafCreateCommand(stockOrderRedLeafId, quantity);
      var result = testKit.call(e -> e.createStockOrderRedLeaf(command));
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
  void singleStockOrderConsumesOrderSkuItemsTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedLeafEntity::new);
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId", "skuId");

    var quantityStockOrder = 17;
    var quantityRequested = 10;

    {
      var command = new StockOrderRedLeafEntity.StockOrderRedLeafCreateCommand(stockOrderRedLeafId, quantityStockOrder);
      var result = testKit.call(e -> e.createStockOrderRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // first request gets no stockSkuItems because orderItem is not yet available to be consumed
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-1", "skuId");
      var orderSkuItemIds = IntStream.range(0, quantityRequested)
          .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
          .toList();
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItemIds);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(0, event.stockSkuItemsConsumed().size());
    }

    { // set available to be consumed
      var command = new StockOrderRedLeafEntity.StockOrderSetAvailableToBeConsumedCommand(stockOrderRedLeafId);
      var result = testKit.call(e -> e.stockOrderSetAvailableToBeConsumed(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // second request gets stockSkuItems because orderItem is now available to be consumed
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-2", "skuId");
      var orderSkuItemIds = IntStream.range(0, quantityRequested)
          .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
          .toList();
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItemIds);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(1, event.stockSkuItemsConsumed().size());
      assertEquals(quantityRequested, event.stockSkuItemsConsumed().get(0).stockSkuItemsToOrderSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(quantityStockOrder - quantityRequested, state.stockSkuItemsAvailable().size());
    }
  }

  EventSourcedTestKit<State, Event, StockOrderRedLeafEntity> createStockOrderRedLeaf(StockOrderRedLeafId stockOrderRedLeafId, int quantityStockOrder, boolean makeAvailableToConsume) {
    var testKit = EventSourcedTestKit.of(StockOrderRedLeafEntity::new);

    {
      var command = new StockOrderRedLeafEntity.StockOrderRedLeafCreateCommand(stockOrderRedLeafId, quantityStockOrder);
      var result = testKit.call(e -> e.createStockOrderRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    if (makeAvailableToConsume) {
      var command = new StockOrderRedLeafEntity.StockOrderSetAvailableToBeConsumedCommand(stockOrderRedLeafId);
      var result = testKit.call(e -> e.stockOrderSetAvailableToBeConsumed(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    return testKit;
  }

  @Test
  void singleStockOrderConsumesOrderSkuItemsIdempotentTest() {
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId", "skuId");

    var quantityStockOrder = 17;
    var quantityRequested = 10;

    var testKit = createStockOrderRedLeaf(stockOrderRedLeafId, quantityStockOrder, true);

    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-1", "skuId");
    var orderSkuItems = IntStream.range(0, quantityRequested)
        .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
        .toList();

    { // first request
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(1, event.stockSkuItemsConsumed().size());
      assertEquals(quantityRequested, event.stockSkuItemsConsumed().get(0).stockSkuItemsToOrderSkuItems().size());
    }

    { // second request
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(0, eventCount);
    }

    {
      var state = testKit.getState();
      assertEquals(quantityStockOrder - quantityRequested, state.stockSkuItemsAvailable().size());
    }
  }

  @Test
  void allocateStockSkuItemsToMultipleOrderItemsTest() {
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId", "skuId");

    var quantityStockOrder = 17;
    var quantityRequested1 = 10;
    var quantityRequested2 = 11;
    var quantityRequested3 = 12;

    var testKit = createStockOrderRedLeaf(stockOrderRedLeafId, quantityStockOrder, true);

    { // this order will be fully allocated
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-1", "skuId");
      var orderSkuItems = IntStream.range(0, quantityRequested1)
          .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
          .toList();
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(1, event.stockSkuItemsConsumed().size());
      assertEquals(quantityRequested1, event.stockSkuItemsConsumed().get(0).stockSkuItemsToOrderSkuItems().size());
    }

    { // this order will be partially allocated
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-2", "skuId");
      var orderSkuItems = IntStream.range(0, quantityRequested2)
          .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
          .toList();
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      var quantityExpected = Math.max(0, quantityStockOrder - quantityRequested1);
      assertEquals(2, event.stockSkuItemsConsumed().size());
      assertEquals(quantityExpected, event.stockSkuItemsConsumed().get(1).stockSkuItemsToOrderSkuItems().size());
    }

    { // this order will not be allocated
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-3", "skuId");
      var orderSkuItems = IntStream.range(0, quantityRequested3)
          .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
          .toList();
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
    }
  }

  @Test
  void multipleOrderItemssConsumeStockSkuItemsAndReleaseOrderTest() {
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId", "skuId");

    var quantityStockOrder = 17;
    var quantityRequested1 = 10;
    var quantityRequested2 = 11;
    var quantityRequested3 = 12;

    var testKit = createStockOrderRedLeaf(stockOrderRedLeafId, quantityStockOrder, true);

    { // this order will be fully allocated
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-1", "skuId");
      var orderSkuItems = IntStream.range(0, quantityRequested1)
          .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
          .toList();
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(1, event.stockSkuItemsConsumed().size());
      assertEquals(quantityRequested1, event.stockSkuItemsConsumed().get(0).stockSkuItemsToOrderSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(quantityStockOrder - quantityRequested1, state.stockSkuItemsAvailable().size());
      assertTrue(state.availableToBeConsumed());
    }

    { // this order will be partially allocated
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-2", "skuId");
      var orderSkuItems = IntStream.range(0, quantityRequested2)
          .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
          .toList();
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      var quantityExpected = Math.max(0, quantityStockOrder - quantityRequested1);
      assertEquals(2, event.stockSkuItemsConsumed().size());
      assertEquals(quantityExpected, event.stockSkuItemsConsumed().get(1).stockSkuItemsToOrderSkuItems().size());
    }

    {
      var state = testKit.getState();
      assertTrue(state.stockSkuItemsAvailable().isEmpty());
    }

    { // release order-2 stockSkuItems
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-2", "skuId");
      var command = new StockOrderRedLeafEntity.OrderItemReleaseStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId);
      var result = testKit.call(e -> e.orderItemReleaseOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var state = testKit.getState();
      assertEquals(quantityStockOrder - quantityRequested1, state.stockSkuItemsAvailable().size());
    }

    { // this order will not be allocated because it is no longer available to be consumed
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-3", "skuId");
      var orderSkuItems = IntStream.range(0, quantityRequested3)
          .mapToObj(i -> OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId))
          .toList();
      var command = new StockOrderRedLeafEntity.OrderItemRequestsStockSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, orderSkuItems);
      var result = testKit.call(e -> e.orderItemRequestsStockSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
    }
  }

  @Test
  void consumeOrderSkuItemsToStockOrderTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedLeafEntity::new);
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId", "skuId");

    var quantityStockOrder = 17;
    var quantityOrderSkuItems = 10;

    {
      var command = new StockOrderRedLeafEntity.StockOrderRedLeafCreateCommand(stockOrderRedLeafId, quantityStockOrder);
      var result = testKit.call(e -> e.createStockOrderRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    var state = testKit.getState();
    var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-1", "skuId");
    var stockSkuItems = state.stockSkuItemsAvailable().stream()
        .map(stockSkuItemId -> new StockOrderRedLeafEntity.StockSkuItemToOrderSkuItem(stockSkuItemId,
            OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId)))
        .limit(quantityOrderSkuItems)
        .toList();

    {
      var command = new StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, stockSkuItems);
      var result = testKit.call(e -> e.stockOrderConsumedOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(1, event.stockSkuItemsConsumed().size());
      assertEquals(quantityOrderSkuItems, event.stockSkuItemsConsumed().get(0).stockSkuItemsToOrderSkuItems().size());

      var eventRequest = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent.class);
      assertEquals(quantityStockOrder - quantityOrderSkuItems, eventRequest.stockSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityAllocated = stateAfter.stockSkuItemsAvailable().size();
      assertEquals(quantityStockOrder - quantityOrderSkuItems, quantityAllocated);
    }

    { // idempotent test
      var command = new StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, stockSkuItems);
      var result = testKit.call(e -> e.stockOrderConsumedOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(0, eventCount);
    }
  }

  @Test
  void consumeOrderSkuItemsForTwoStockOrdersTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedLeafEntity::new);
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-1", "skuId");

    var quantityStockOrder = 17;
    var quantityOrderSkuItems1 = 9;
    var quantityOrderSkuItems2 = 8;

    {
      var command = new StockOrderRedLeafEntity.StockOrderRedLeafCreateCommand(stockOrderRedLeafId, quantityStockOrder);
      var result = testKit.call(e -> e.createStockOrderRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // first order item
      var state = testKit.getState();
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-1", "skuId");
      var stockSkuItems = state.stockSkuItemsAvailable().stream()
          .map(stockSkuItemId -> new StockOrderRedLeafEntity.StockSkuItemToOrderSkuItem(stockSkuItemId,
              OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId)))
          .limit(quantityOrderSkuItems1)
          .toList();

      var command = new StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, stockSkuItems);
      var result = testKit.call(e -> e.stockOrderConsumedOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(1, event.stockSkuItemsConsumed().size());
      assertEquals(quantityOrderSkuItems1, event.stockSkuItemsConsumed().get(0).stockSkuItemsToOrderSkuItems().size());

      var eventRequest = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent.class);
      assertEquals(quantityStockOrder - quantityOrderSkuItems1, eventRequest.stockSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityAllocated = stateAfter.stockSkuItemsAvailable().size();
      assertEquals(quantityStockOrder - quantityOrderSkuItems1, quantityAllocated);
    }

    { // second order item
      var state = testKit.getState();
      var orderItemRedLeafId = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-2", "skuId");
      var stockSkuItems = state.stockSkuItemsAvailable().stream()
          .map(stockSkuItemId -> new StockOrderRedLeafEntity.StockSkuItemToOrderSkuItem(stockSkuItemId,
              OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId)))
          .limit(quantityOrderSkuItems2)
          .toList();

      var command = new StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId, stockSkuItems);
      var result = testKit.call(e -> e.stockOrderConsumedOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(1, eventCount); // only one event because stock order is fully allocated and no request event is emitted

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId, event.orderItemRedLeafId());
      assertEquals(2, event.stockSkuItemsConsumed().size());
      assertEquals(quantityOrderSkuItems2, event.stockSkuItemsConsumed().get(1).stockSkuItemsToOrderSkuItems().size());

      var stateAfter = testKit.getState();
      var quantityRemaining = stateAfter.stockSkuItemsAvailable().size();
      assertEquals(quantityStockOrder - quantityOrderSkuItems1 - quantityOrderSkuItems2, quantityRemaining);
    }
  }

  @Test
  void consumeOrderSkuItemsToStockOrderFRomTwoOrderItemsOneReleasedTest() {
    var testKit = EventSourcedTestKit.of(StockOrderRedLeafEntity::new);
    var stockOrderRedLeafId = StockOrderRedLeafEntity.StockOrderRedLeafId.of("stockOrderId-1", "skuId");

    var quantityStockOrder = 17;
    var quantityOrderSkuItems1 = 9;
    var quantityOrderSkuItems2 = 8;

    {
      var command = new StockOrderRedLeafEntity.StockOrderRedLeafCreateCommand(stockOrderRedLeafId, quantityStockOrder);
      var result = testKit.call(e -> e.createStockOrderRedLeaf(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    // Assign the first order item to the stockSkuItems
    var state = testKit.getState();
    var orderItemRedLeafId1 = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-1", "skuId");
    var stockSkuItems1 = state.stockSkuItemsAvailable().stream()
        .map(stockSkuItemId -> new StockOrderRedLeafEntity.StockSkuItemToOrderSkuItem(stockSkuItemId,
            OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId1)))
        .limit(quantityOrderSkuItems1)
        .toList();

    var orderItemRedLeafId2 = OrderItemRedLeafEntity.OrderItemRedLeafId.of("orderItemId-2", "skuId");
    var stockSkuItems2 = state.stockSkuItemsAvailable().stream()
        .map(stockSkuItemId -> new StockOrderRedLeafEntity.StockSkuItemToOrderSkuItem(stockSkuItemId,
            OrderItemRedLeafEntity.OrderSkuItemId.of(orderItemRedLeafId2)))
        .limit(quantityOrderSkuItems2)
        .toList();

    { // first order item
      var command = new StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId1, stockSkuItems1);
      var result = testKit.call(e -> e.stockOrderConsumedOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount); // two events because stock order is not fully allocated and a request event is emitted

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId1, event.orderItemRedLeafId());
      assertEquals(1, event.stockSkuItemsConsumed().size());
      assertEquals(quantityOrderSkuItems1, event.stockSkuItemsConsumed().get(0).stockSkuItemsToOrderSkuItems().size());

      var eventRequest = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent.class);
      assertEquals(quantityStockOrder - quantityOrderSkuItems1, eventRequest.stockSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityAvailable = stateAfter.stockSkuItemsAvailable().size();
      assertEquals(quantityStockOrder - quantityOrderSkuItems1, quantityAvailable);
    }

    { // second order item released because some of the stockSkuItems are already consumed
      var command = new StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsCommand(stockOrderRedLeafId, orderItemRedLeafId2, stockSkuItems2);
      var result = testKit.call(e -> e.stockOrderConsumedOrderSkuItems(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var eventCount = result.getAllEvents().size();
      assertEquals(2, eventCount);

      var event = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderReleasedOrderSkuItemsEvent.class);
      assertEquals(stockOrderRedLeafId, event.stockOrderRedLeafId());
      assertEquals(orderItemRedLeafId2, event.orderItemRedLeafId());

      var eventRequest = result.getNextEventOfType(StockOrderRedLeafEntity.StockOrderRequestsOrderSkuItemsEvent.class);
      assertEquals(quantityStockOrder - quantityOrderSkuItems1, eventRequest.stockSkuItemIds().size());

      var stateAfter = testKit.getState();
      var quantityAvailable = stateAfter.stockSkuItemsAvailable().size();
      assertEquals(quantityStockOrder - quantityOrderSkuItems1, quantityAvailable);
    }
  }
}
