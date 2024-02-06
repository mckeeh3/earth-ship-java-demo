package io.example.shipping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import kalix.javasdk.testkit.EventSourcedTestKit;

public class ShippingOrderEntityTest {
  @Test
  void shippingOrderCreateTest() {
    var testKit = EventSourcedTestKit.of(ShippingOrderEntity::new);

    var orderId = "order-1";
    var customerId = "customer-1";
    var orderedAt = Instant.now();
    var orderItem = new ShippingOrderEntity.OrderItem("sku-1", "product-1", 1, null, null);
    var orderItems = List.of(orderItem);

    {
      var command = new ShippingOrderEntity.ShippingOrderCreateCommand(orderId, customerId, orderedAt, orderItems);
      var result = testKit.call(e -> e.shippingOrderCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(ShippingOrderEntity.ShippingOrderCreatedEvent.class);
      assertEquals(orderId, event.orderId());
      assertEquals(customerId, event.customerId());
      assertEquals(orderedAt, event.orderedAt());
      assertEquals(1, event.orderItems().size());
      assertEquals(orderItem, event.orderItems().get(0));
    }

    {
      var state = testKit.getState();
      assertEquals(orderId, state.orderId());
      assertEquals(customerId, state.customerId());
      assertEquals(orderedAt, state.orderedAt());
      assertEquals(orderItems, state.orderItems());
    }

    { // Idempotent test
      var command = new ShippingOrderEntity.ShippingOrderCreateCommand(orderId, customerId, orderedAt, orderItems);
      var result = testKit.call(e -> e.shippingOrderCreate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertTrue(result.getAllEvents().isEmpty());
    }
  }

  @Test
  void getShippingOrderTest() {
    var testKit = EventSourcedTestKit.of(ShippingOrderEntity::new);

    var orderItems = List.of(
        new ShippingOrderEntity.OrderItem("sku-1", "skuName-1", 1, null, null),
        new ShippingOrderEntity.OrderItem("sku-2", "skuName-2", 2, null, null));

    {
      var command = new ShippingOrderEntity.ShippingOrderCreateCommand("order-1", "customer-1", Instant.now(), orderItems);
      testKit.call(e -> e.shippingOrderCreate(command));
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
    var testKit = EventSourcedTestKit.of(ShippingOrderEntity::new);

    var orderId = "order-1";
    var customerId = "customer-1";
    var orderedAt = Instant.now();
    var skuId = "sku-1";
    var skuName = "product-1";
    var quantity = 1;
    var readyToShipAt = Instant.now();
    var orderItem = new ShippingOrderEntity.OrderItem(skuId, skuName, quantity, null, null);
    var orderItems = List.of(orderItem);

    {
      var command = new ShippingOrderEntity.ShippingOrderCreateCommand(orderId, customerId, orderedAt, orderItems);
      testKit.call(e -> e.shippingOrderCreate(command));
    }

    {
      var command = new ShippingOrderEntity.OrderItemUpdateCommand(orderId, skuId, readyToShipAt, null);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(ShippingOrderEntity.OrderItemUpdatedEvent.class);
        assertEquals(orderId, event.orderId());
        assertEquals(skuId, event.skuId());
        assertNotNull(event.orderReadyToShipAt());
        assertEquals(1, event.orderItems().size());
        assertEquals(readyToShipAt, event.orderItems().get(0).readyToShipAt());
      }

      {
        var state = testKit.getState();
        assertNotNull(state.readyToShipAt());
        assertNull(state.backOrderedAt());
        assertEquals(1, state.orderItems().size());
        assertEquals(readyToShipAt, state.orderItems().get(0).readyToShipAt());
        assertNull(state.orderItems().get(0).backOrderedAt());
      }
    }
  }

  @Test
  void backOrderOneOfTwoOrderItemsTest() {
    var testKit = EventSourcedTestKit.of(ShippingOrderEntity::new);

    var orderId = "order-1";
    var customerId = "customer-1";
    var orderedAt = Instant.now();
    var skuId1 = "sku-1";
    var skuName1 = "product-1";
    var quantity1 = 1;
    var skuId2 = "sku-2";
    var skuName2 = "product-2";
    var quantity2 = 1;
    var readyToShipAt = Instant.now();
    var backOrderedAt = Instant.now();
    var orderItem1 = new ShippingOrderEntity.OrderItem(skuId1, skuName1, quantity1, null, null);
    var orderItem2 = new ShippingOrderEntity.OrderItem(skuId2, skuName2, quantity2, null, null);
    var orderItems = List.of(orderItem1, orderItem2);

    {
      var command = new ShippingOrderEntity.ShippingOrderCreateCommand(orderId, customerId, orderedAt, orderItems);
      testKit.call(e -> e.shippingOrderCreate(command));
    }

    {
      var command = new ShippingOrderEntity.OrderItemUpdateCommand(orderId, skuId1, readyToShipAt, null);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(ShippingOrderEntity.OrderItemUpdatedEvent.class);
        assertEquals(orderId, event.orderId());
        assertEquals(skuId1, event.skuId());
        assertNull(event.orderReadyToShipAt());
        assertEquals(2, event.orderItems().size());
        assertEquals(readyToShipAt, event.orderItems().get(0).readyToShipAt());
      }

      {
        var state = testKit.getState();
        assertNull(state.readyToShipAt());
        assertNull(state.backOrderedAt());
        assertEquals(2, state.orderItems().size());
        assertEquals(readyToShipAt, state.orderItems().get(0).readyToShipAt());
      }
    }

    {
      var command = new ShippingOrderEntity.OrderItemUpdateCommand(orderId, skuId2, null, backOrderedAt);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(ShippingOrderEntity.OrderItemUpdatedEvent.class);
        assertEquals(orderId, event.orderId());
        assertEquals(skuId2, event.skuId());
        assertNull(event.orderReadyToShipAt());
        assertNotNull(event.orderBackOrderedAt());
        assertEquals(2, event.orderItems().size());
        assertEquals(backOrderedAt, event.orderItems().get(1).backOrderedAt());
      }

      {
        var state = testKit.getState();
        assertNull(state.readyToShipAt());
        assertNotNull(state.backOrderedAt());
        assertEquals(2, state.orderItems().size());
        assertEquals(backOrderedAt, state.orderItems().get(1).backOrderedAt());
      }
    }
  }

  @Test
  void readyToShipWithTwoOrderItemsTest() {
    var testKit = EventSourcedTestKit.of(ShippingOrderEntity::new);

    var orderId = "order-1";
    var customerId = "customer-1";
    var orderedAt = Instant.now();
    var skuId1 = "sku-1";
    var skuName1 = "product-1";
    var quantity1 = 1;
    var skuId2 = "sku-2";
    var skuName2 = "product-2";
    var quantity2 = 1;
    var readyToShipAt = Instant.now();
    var orderItem1 = new ShippingOrderEntity.OrderItem(skuId1, skuName1, quantity1, null, null);
    var orderItem2 = new ShippingOrderEntity.OrderItem(skuId2, skuName2, quantity2, null, null);
    var orderItems = List.of(orderItem1, orderItem2);

    {
      var command = new ShippingOrderEntity.ShippingOrderCreateCommand(orderId, customerId, orderedAt, orderItems);
      testKit.call(e -> e.shippingOrderCreate(command));
    }

    {
      var command = new ShippingOrderEntity.OrderItemUpdateCommand(orderId, skuId1, readyToShipAt, null);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(ShippingOrderEntity.OrderItemUpdatedEvent.class);
        assertEquals(orderId, event.orderId());
        assertEquals(skuId1, event.skuId());
        assertNull(event.orderReadyToShipAt());
        assertNull(event.orderBackOrderedAt());
        assertEquals(2, event.orderItems().size());
        assertEquals(readyToShipAt, event.orderItems().get(0).readyToShipAt());
      }

      {
        var state = testKit.getState();
        assertNull(state.readyToShipAt());
        assertNull(state.backOrderedAt());
        assertEquals(2, state.orderItems().size());
        assertEquals(readyToShipAt, state.orderItems().get(0).readyToShipAt());
        assertNull(state.orderItems().get(0).backOrderedAt());
      }
    }

    {
      var command = new ShippingOrderEntity.OrderItemUpdateCommand(orderId, skuId2, readyToShipAt, null);
      var result = testKit.call(e -> e.orderItemUpdate(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(ShippingOrderEntity.OrderItemUpdatedEvent.class);
        assertEquals(orderId, event.orderId());
        assertEquals(skuId2, event.skuId());
        assertNotNull(event.orderReadyToShipAt());
        assertEquals(2, event.orderItems().size());
        assertEquals(readyToShipAt, event.orderItems().get(1).readyToShipAt());
      }

      {
        var state = testKit.getState();
        assertNotNull(state.readyToShipAt());
        assertNull(state.backOrderedAt());
        assertEquals(2, state.orderItems().size());
        assertEquals(readyToShipAt, state.orderItems().get(0).readyToShipAt());
        assertEquals(readyToShipAt, state.orderItems().get(1).readyToShipAt());
      }
    }
  }
}
