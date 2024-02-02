package io.example.product;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

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
  void updateBackOrderTest() {
    var testKit = EventSourcedTestKit.of(ProductEntity::new);

    var skuId = "skuId";
    var skuName = "skuName";
    var skuDescription = "skuDescription";
    var price = BigDecimal.ONE;
    var quantityBackOrdered = 10;

    {
      var command = new ProductEntity.CreateProductCommand(skuId, skuName, skuDescription, price);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var command = new ProductEntity.UpdateBackOrderedCommand(skuId, quantityBackOrdered);
      var result = testKit.call(e -> e.updateBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(3, result.getAllEvents().size());
      {
        var event = result.getNextEventOfType(ProductEntity.UpdatedBackOrderedEvent.class);
        assertEquals(skuId, event.skuId());
        assertEquals(quantityBackOrdered, event.quantityBackOrdered());
      }

      {
        var event = result.getNextEventOfType(ProductEntity.AddedStockOrderEvent.class);
        assertEquals(ProductEntity.quantityPerStockOrder, event.quantityTotal());
      }

      {
        var event = result.getNextEventOfType(ProductEntity.CreateStockOrderRequestedEvent.class);
        assertEquals(skuId, event.skuId());
        assertEquals(ProductEntity.quantityPerStockOrder, event.quantityTotal());
      }
    }

    {
      quantityBackOrdered = 20;
      var command = new ProductEntity.UpdateBackOrderedCommand(skuId, quantityBackOrdered);
      var result = testKit.call(e -> e.updateBackOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(ProductEntity.UpdatedBackOrderedEvent.class);
      assertEquals(skuId, event.skuId());
      assertEquals(quantityBackOrdered, event.quantityBackOrdered());
    }

    {
      var state = testKit.getState();
      assertEquals(skuId, state.skuId());
      assertEquals(skuName, state.skuName());
      assertEquals(skuDescription, state.skuDescription());
      assertEquals(price, state.skuPrice());
      assertEquals(ProductEntity.quantityPerStockOrder, state.quantityAvailable());
      assertEquals(quantityBackOrdered, state.quantityBackOrdered());
    }
  }
}
