package io.example.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.example.map.WorldMap.LatLng;
import kalix.javasdk.testkit.EventSourcedTestKit;

public class GeoOrderEntityTest {
  @Test
  public void createTest() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    {
      var position = new LatLng(0, 0);
      var command = new GeoOrderEntity.CreateGeoOrderCommand("geoOrderId", position);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(GeoOrderEntity.GeoOrderCreatedEvent.class);
      assertEquals("geoOrderId", event.geoOrderId());
      assertEquals(position, event.position());

      var state = testKit.getState();
      assertEquals("geoOrderId", state.geoOrderId());
      assertEquals(position, state.position());
      assertNull(state.readyToShipAt());
      assertNull(state.backOrderedAt());
    }
  }

  @Test
  public void createIdempotentTest() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    {
      var position = new LatLng(0, 0);
      var command = new GeoOrderEntity.CreateGeoOrderCommand("geoOrderId", position);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var position = new LatLng(0, 0);
      var command = new GeoOrderEntity.CreateGeoOrderCommand("geoOrderId", position);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
      assertEquals(0, result.getAllEvents().size());
    }
  }

  @Test
  public void readyToShipTest() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    var position = new LatLng(0, 0);
    {
      var command = new GeoOrderEntity.CreateGeoOrderCommand("geoOrderId", position);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var readyToShipAt = Instant.now();
      var command = new GeoOrderEntity.GeoOrderReadyToShipCommand("geoOrderId", readyToShipAt);
      var result = testKit.call(e -> e.readyToShip(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(GeoOrderEntity.GeoOrderReadyToShipEvent.class);
      assertEquals("geoOrderId", event.geoOrderId());
      assertEquals(position, event.position());
      assertEquals(readyToShipAt, event.readyToShipAt());

      var state = testKit.getState();
      assertEquals("geoOrderId", state.geoOrderId());
      assertEquals(position, state.position());
      assertEquals(readyToShipAt, state.readyToShipAt());
      assertNull(state.backOrderedAt());
    }
  }

  @Test
  public void backOrderedTest() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    var position = new LatLng(0, 0);
    {
      var command = new GeoOrderEntity.CreateGeoOrderCommand("geoOrderId", position);
      var result = testKit.call(e -> e.create(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());
    }

    {
      var backOrderedAt = Instant.now();
      var command = new GeoOrderEntity.GeoOrderBackOrderedCommand("geoOrderId", backOrderedAt);
      var result = testKit.call(e -> e.backOrdered(command));
      assertTrue(result.isReply());
      assertEquals("OK", result.getReply());

      var event = result.getNextEventOfType(GeoOrderEntity.GeoOrderBackOrderedEvent.class);
      assertEquals("geoOrderId", event.geoOrderId());
      assertEquals(position, event.position());
      assertEquals(backOrderedAt, event.backOrderedAt());

      var state = testKit.getState();
      assertEquals("geoOrderId", state.geoOrderId());
      assertEquals(position, state.position());
      assertNull(state.readyToShipAt());
      assertEquals(backOrderedAt, state.backOrderedAt());
    }
  }
}
