package io.example.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import io.example.stock.StockOrderEntity;
import kalix.javasdk.testkit.EventSourcedTestKit;

public class ProductEntityTest {
  @Test
  void createTest() {
    var testKit = EventSourcedTestKit.of(ProductEntity::new);

    var skuId = "skuId";
    var skuName = "skuName";
    var skuDescription = "skuDescription";
    var price = BigDecimal.ONE;

    {
      var command = new ProductEntity.CreateProductCommand(skuId, skuName, skuDescription, price);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(ProductEntity.CreatedProductEvent.class);
      assertEquals(skuId, event.skuId());
      assertEquals(skuName, event.skuName());
      assertEquals(skuDescription, event.skuDescription());
      assertEquals(price, event.skuPrice());
    }

    {
      var state = testKit.getState();
      assertEquals(skuId, state.skuId());
      assertEquals(skuName, state.skuName());
      assertEquals(skuDescription, state.skuDescription());
      assertEquals(price, state.skuPrice());
      assertEquals(0, state.quantityBackOrdered());
      assertEquals(0, state.quantityBackOrdered());
    }

    { // idempotent test
      var command = new ProductEntity.CreateProductCommand(skuId, skuName, skuDescription, price);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(0, result.getAllEvents().size());
    }
  }

  @Test
  void addStockOrderTest() {
    var testKit = EventSourcedTestKit.of(ProductEntity::new);

    var skuId = "skuId";
    var skuName = "skuName";
    var skuDescription = "skuDescription";
    var price = BigDecimal.ONE;
    var stockOrderId = StockOrderEntity.genStockOrderId();
    var quantityTotal = 100;

    {
      var command = new ProductEntity.CreateProductCommand(skuId, skuName, skuDescription, price);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new ProductEntity.AddStockOrderCommand(stockOrderId, skuId, quantityTotal);
      var result = testKit.call(e -> e.addStockOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());
      {
        var event = result.getNextEventOfType(ProductEntity.AddedStockOrderEvent.class);
        assertEquals(stockOrderId, event.stockOrderId());
        assertEquals(skuId, event.skuId());
        assertEquals(quantityTotal, event.quantityTotal());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(skuId, state.skuId());
      assertEquals(skuName, state.skuName());
      assertEquals(skuDescription, state.skuDescription());
      assertEquals(price, state.skuPrice());
      assertEquals(quantityTotal, state.quantityAvailable());
      assertEquals(0, state.quantityBackOrdered());
      assertEquals(1, state.stockOrders().size());
      assertEquals(stockOrderId, state.stockOrders().get(0).stockOrderId());
    }

    { // idempotent test
      var command = new ProductEntity.AddStockOrderCommand(stockOrderId, skuId, quantityTotal);
      var result = testKit.call(e -> e.addStockOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(0, result.getAllEvents().size());
    }
  }

  @Test
  void updateBackOrderTest() {
    var testKit = EventSourcedTestKit.of(ProductEntity::new);

    var skuId = "skuId";
    var skuName = "skuName";
    var skuDescription = "skuDescription";
    var price = BigDecimal.ONE;

    {
      var command = new ProductEntity.CreateProductCommand(skuId, skuName, skuDescription, price);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var quantityBackOrdered = 10;
      var command = new ProductEntity.UpdateBackOrderedCommand(skuId, quantityBackOrdered);
      var result = testKit.call(e -> e.updateBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(2, result.getAllEvents().size());
      {
        var event = result.getNextEventOfType(ProductEntity.UpdatedBackOrderedEvent.class);
        assertEquals(skuId, event.skuId());
        assertEquals(quantityBackOrdered, event.quantityBackOrdered());
      }

      {
        var event = result.getNextEventOfType(ProductEntity.CreateStockOrderRequestedEvent.class);
        assertEquals(skuId, event.skuId());
        assertEquals(ProductEntity.quantityPerStockOrder, event.quantityTotal());
      }
    }

    {
      var quantityBackOrdered = 20;
      var command = new ProductEntity.UpdateBackOrderedCommand(skuId, quantityBackOrdered);
      var result = testKit.call(e -> e.updateBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(ProductEntity.UpdatedBackOrderedEvent.class);
      assertEquals(skuId, event.skuId());
      assertEquals(quantityBackOrdered, event.quantityBackOrdered());

      var state = testKit.getState();
      assertEquals(skuId, state.skuId());
      assertEquals(skuName, state.skuName());
      assertEquals(skuDescription, state.skuDescription());
      assertEquals(price, state.skuPrice());
      assertEquals(0, state.quantityAvailable());
      assertEquals(quantityBackOrdered, state.quantityBackOrdered());
      assertEquals(0, state.stockOrders().size());
    }
  }

  @Test
  void updateStockOrderTest() {
    var testKit = EventSourcedTestKit.of(ProductEntity::new);

    var stockOrderId = StockOrderEntity.genStockOrderId();
    var skuId = "skuId";
    var skuName = "skuName";
    var skuDescription = "skuDescription";
    var price = BigDecimal.ONE;
    var quantityAvailable = 100;
    var quantityOrdered = 34;

    {
      var command = new ProductEntity.CreateProductCommand(skuId, skuName, skuDescription, price);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new ProductEntity.AddStockOrderCommand(stockOrderId, skuId, quantityAvailable);
      var result = testKit.call(e -> e.addStockOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new ProductEntity.UpdateStockOrderCommand(stockOrderId, skuId, quantityOrdered);
      var result = testKit.call(e -> e.updateStockOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(ProductEntity.UpdatedStockOrderEvent.class);
        assertEquals(skuId, event.skuId());
        assertEquals(quantityAvailable - quantityOrdered, event.quantityAvailable());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(skuId, state.skuId());
      assertEquals(skuName, state.skuName());
      assertEquals(skuDescription, state.skuDescription());
      assertEquals(price, state.skuPrice());
      assertEquals(quantityAvailable - quantityOrdered, state.quantityAvailable());
      assertEquals(0, state.quantityBackOrdered());
    }
  }

  @Test
  void updateStockOrderThatTriggersLowStockAvailableTest() {
    var testKit = EventSourcedTestKit.of(ProductEntity::new);

    var stockOrderId = StockOrderEntity.genStockOrderId();
    var skuId = "skuId";
    var skuName = "skuName";
    var skuDescription = "skuDescription";
    var price = BigDecimal.ONE;
    var quantityTotal = ProductEntity.quantityPerStockOrder;
    var quantityOrdered = 89;

    {
      var command = new ProductEntity.CreateProductCommand(skuId, skuName, skuDescription, price);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new ProductEntity.AddStockOrderCommand(stockOrderId, skuId, quantityTotal);
      var result = testKit.call(e -> e.addStockOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new ProductEntity.UpdateStockOrderCommand(stockOrderId, skuId, quantityOrdered);
      var result = testKit.call(e -> e.updateStockOrder(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(ProductEntity.UpdatedStockOrderEvent.class);
        assertEquals(skuId, event.skuId());
        assertEquals(quantityTotal - quantityOrdered, event.quantityAvailable());
      }

      {
        var event = result.getNextEventOfType(ProductEntity.CreateStockOrderRequestedEvent.class);
        assertEquals(skuId, event.skuId());
        assertEquals(ProductEntity.quantityPerStockOrder, event.quantityTotal());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(skuId, state.skuId());
      assertEquals(skuName, state.skuName());
      assertEquals(skuDescription, state.skuDescription());
      assertEquals(price, state.skuPrice());
      assertEquals(quantityTotal - quantityOrdered, state.quantityAvailable());
      assertEquals(0, state.quantityBackOrdered());
      assertEquals(1, state.stockOrders().size());
    }
  }
}
