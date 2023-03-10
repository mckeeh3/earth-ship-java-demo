package io.example.map;

import static io.example.map.WorldMap.latLng;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.map.WorldMap.LatLng;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("geoOrderId")
@EntityType("geoOrder")
@RequestMapping("/geo-order/{geoOrderId}")
public class GeoOrderEntity extends EventSourcedEntity<GeoOrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(GeoOrderEntity.class);
  private final String entityId;

  public GeoOrderEntity(EventSourcedEntityContext context) {
    entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.empty();
  }

  @PostMapping("/create")
  public Effect<String> create(@RequestBody CreateGeoOrderCommand command) {
    log.debug("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/ready-to-ship")
  public Effect<String> readyToShip(@RequestBody GeoOrderReadyToShipCommand command) {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());
    if (currentState().isEmpty()) {
      return effects().reply("OK");
    }
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/back-ordered")
  public Effect<String> alarm(@RequestBody GeoOrderBackOrderedCommand command) {
    log.debug("EntityId: {}\n_State: {}", entityId, currentState());
    if (currentState().isEmpty()) {
      return effects().reply("OK");
    }
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping()
  public Effect<GeoOrderEntity.State> get(@PathVariable String geoOrderId) {
    log.debug("EntityId: {}\n_GeoOrderId: {}\n_State: {}", entityId, geoOrderId, currentState());
    if (currentState().isEmpty()) {
      return effects().error("GeoOrder: '%s' not created".formatted(geoOrderId));
    }
    return effects().reply(currentState());
  }

  @EventHandler
  public State on(GeoOrderCreatedEvent event) {
    log.debug("State: {}\n_Event: {}", currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(GeoOrderReadyToShipEvent event) {
    log.debug("State: {}\n_Event: {}", currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(GeoOrderBackOrderedEvent event) {
    log.debug("State: {}\n_Event: {}", currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String geoOrderId,
      LatLng position,
      Instant readyToShipAt,
      Instant backOrderedAt) {

    static State empty() {
      return new State(null, latLng(0, 0), null, null);
    }

    boolean isEmpty() {
      return geoOrderId == null;
    }

    GeoOrderCreatedEvent eventFor(CreateGeoOrderCommand command) {
      return new GeoOrderCreatedEvent(command.geoOrderId, command.position);
    }

    GeoOrderReadyToShipEvent eventFor(GeoOrderReadyToShipCommand command) {
      return new GeoOrderReadyToShipEvent(geoOrderId, position, command.readyToShipAt);
    }

    GeoOrderBackOrderedEvent eventFor(GeoOrderBackOrderedCommand command) {
      return new GeoOrderBackOrderedEvent(geoOrderId, position, command.backOrderedAt);
    }

    State on(GeoOrderCreatedEvent event) {
      return geoOrderId != null
          ? this
          : new State(event.geoOrderId, event.position, null, null);
    }

    State on(GeoOrderReadyToShipEvent event) {
      return new State(geoOrderId, position, event.readyToShipAt, null);
    }

    State on(GeoOrderBackOrderedEvent event) {
      return new State(geoOrderId, position, null, event.backOrderedAt);
    }
  }

  static String geoOrderIdFor(LatLng position) {
    return "geoOrder-id_%1.13f_%1.13f".formatted(position.lat(), position.lng());
  }

  public record CreateGeoOrderCommand(String geoOrderId, LatLng position) {}

  public record GeoOrderCreatedEvent(String geoOrderId, LatLng position) {}

  public record GeoOrderReadyToShipCommand(String geoOrderId, Instant readyToShipAt) {}

  public record GeoOrderReadyToShipEvent(String geoOrderId, LatLng position, Instant readyToShipAt) {}

  public record GeoOrderBackOrderedCommand(String geoOrderId, Instant backOrderedAt) {}

  public record GeoOrderBackOrderedEvent(String geoOrderId, LatLng position, Instant backOrderedAt) {}
}
