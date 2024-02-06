package io.example.order;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class OrderEntityTest {
  @Test
  void orderCreateTest() {
    var testKit = EventSourcedTestKit.of(OrderEntity::new);

    var orderId = "order-1";
    var customerId = "customer-1";
    var orderItems = List.of(
        new OrderEntity.OrderItem("sku-1", "skuName-1", "sku-desc-1", BigDecimal.valueOf(100), 1, null, null),
        new OrderEntity.OrderItem("sku-2", "skuName-2", "sku-desc-2", BigDecimal.valueOf(200), 2, null, null));

    {
      var command = new OrderEntity.CreateOrderCommand(orderId, customerId, orderItems);
      var result = testKit.call(e -> e.createOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(OrderEntity.CreatedOrderEvent.class);
      assertEquals(orderId, event.orderId());
      assertEquals(customerId, event.customerId());
    }

    {
      var state = testKit.getState();
      assertEquals(orderId, state.orderId());
      assertEquals(customerId, state.customerId());
      assertEquals(orderItems, state.orderItems());
    }

    { // Idempotent test
      var command = new OrderEntity.CreateOrderCommand(orderId, customerId, orderItems);
      var result = testKit.call(e -> e.createOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertTrue(result.getAllEvents().isEmpty());
    }
  }

  @Test
  void getOrderTest() {
    var testKit = EventSourcedTestKit.of(OrderEntity::new);

    var orderId = "order-1";
    var customerId = "customer-1";
    var items = List.of(
        new OrderEntity.OrderItem("sku-1", "skuName-1", "sku-desc-1", BigDecimal.valueOf(100), 1, null, null),
        new OrderEntity.OrderItem("sku-2", "skuName-2", "sku-desc-2", BigDecimal.valueOf(200), 2, null, null));

    {
      var command = new OrderEntity.CreateOrderCommand(orderId, customerId, items);
      var result = testKit.call(e -> e.createOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var result = testKit.call(e -> e.get());
      assertTrue(result.isReply());

      var state = testKit.getState();
      assertEquals(state, result.getReply());
    }
  }

  @Test
  void readyToShipWithOneOrderItemTest() {
    var testKit = EventSourcedTestKit.of(OrderEntity::new);

    var orderId = "order-1";
    var customerId = "customer-1";
    var skuId = "sku-1";
    var readyToShipAt = Instant.now();
    var orderItems = List.of(
        new OrderEntity.OrderItem(skuId, "skuName-1", "sku-desc-1", BigDecimal.valueOf(100), 1, null, null));

    {
      var command = new OrderEntity.CreateOrderCommand(orderId, customerId, orderItems);
      var result = testKit.call(e -> e.createOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new OrderEntity.OrderItemUpdateCommand(orderId, skuId, readyToShipAt, null);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(OrderEntity.OrderItemUpdatedEvent.class);
        assertEquals(orderId, event.orderId());
        assertEquals(skuId, event.skuId());
        assertNotNull(event.orderReadyToShipAt());
        assertNull(event.orderBackOrderedAt());
      }

      {
        var event = result.getNextEventOfType(OrderEntity.ReadyToShipOrderEvent.class);
        assertEquals(orderId, event.orderId());
        assertNotNull(event.readyToShipAt());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(1, state.orderItems().size());
      var item = state.orderItems().get(0);
      assertEquals(skuId, item.skuId());
      assertEquals("skuName-1", item.skuName());
      assertEquals("sku-desc-1", item.skuDescription());
      assertEquals(BigDecimal.valueOf(100), item.skuPrice());
      assertEquals(1, item.quantity());
      assertEquals(null, item.backOrderedAt());
      assertEquals(readyToShipAt, item.readyToShipAt());
    }
  }

  @Test
  void backOrderedOneOfTwoOrderItemsTest() {
    var testKit = EventSourcedTestKit.of(OrderEntity::new);

    var orderId = "order-1";
    var customerId = "customer-1";
    var skuId1 = "sku-1";
    var skuId2 = "sku-2";
    var readyToShipAt = Instant.now();
    var backOrderedAt = Instant.now();
    var orderItems = List.of(
        new OrderEntity.OrderItem(skuId1, "skuName-1", "sku-desc-1", BigDecimal.valueOf(100), 1, null, null),
        new OrderEntity.OrderItem(skuId2, "skuName-2", "sku-desc-2", BigDecimal.valueOf(200), 2, null, null));

    {
      var command = new OrderEntity.CreateOrderCommand(orderId, customerId, orderItems);
      var result = testKit.call(e -> e.createOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    { // Set 1st item to back ordered
      var command = new OrderEntity.OrderItemUpdateCommand(orderId, skuId1, null, backOrderedAt);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(OrderEntity.OrderItemUpdatedEvent.class);
        assertNull(event.orderReadyToShipAt());
        assertNotNull(event.orderBackOrderedAt());
      }

      {
        var event = result.getNextEventOfType(OrderEntity.BackOrderedOrderEvent.class);
        assertEquals(orderId, event.orderId());
        assertNotNull(event.backOrderedAt());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(2, state.orderItems().size());
      var orderItem = state.orderItems().get(0);
      assertEquals(backOrderedAt, orderItem.backOrderedAt());
      assertNull(orderItem.readyToShipAt());
    }

    { // Set 2nd item to ready to ship
      var command = new OrderEntity.OrderItemUpdateCommand(orderId, skuId2, readyToShipAt, null);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(OrderEntity.OrderItemUpdatedEvent.class);
        assertNull(event.orderReadyToShipAt());
        assertNotNull(event.orderBackOrderedAt());
      }
    }

    { // Set 1st item to ready to ship }
      var command = new OrderEntity.OrderItemUpdateCommand(orderId, skuId1, readyToShipAt, null);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(OrderEntity.OrderItemUpdatedEvent.class);
        assertNotNull(event.orderReadyToShipAt());
        assertNull(event.orderBackOrderedAt());
      }

      {
        var event = result.getNextEventOfType(OrderEntity.ReadyToShipOrderEvent.class);
        assertEquals(orderId, event.orderId());
        assertNotNull(event.readyToShipAt());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(2, state.orderItems().size());
      var orderItem = state.orderItems().get(0);
      assertNull(orderItem.backOrderedAt());
      assertNotNull(orderItem.readyToShipAt());
    }
  }
}
