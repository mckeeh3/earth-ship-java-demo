package io.example.map;

import static io.example.map.WorldMap.*;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;
import kalix.springsdk.annotations.EventHandler;

@EntityKey("deviceId")
@EntityType("device")
@RequestMapping("/device")
public class DeviceEntity extends EventSourcedEntity<DeviceEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(DeviceEntity.class);
  private static final Random random = new Random();
  private final String entityId;

  public DeviceEntity(EventSourcedEntityContext context) {
    entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.empty();
  }

  @PostMapping("/{deviceId}/create")
  public Effect<String> create(@RequestBody CreateDeviceCommand command) {
    log.debug("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvent(currentState().eventFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/{deviceId}/ping")
  public Effect<String> ping(@PathVariable String deviceId) {
    log.debug("EntityId: {}\nState: {}", entityId, currentState());
    return effects()
        .emitEvents(currentState().eventsFor(deviceId))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/{deviceId}/toggle-alarm")
  public Effect<String> alarm(@PathVariable String deviceId) {
    log.debug("EntityId: {}\nState: {}", entityId, currentState());
    return effects()
        .emitEvent(new AlarmChangedEvent(deviceId, currentState().position, !currentState().alarmOn, Instant.now()))
        .thenReply(__ -> "OK");
  }

  @GetMapping("/{deviceId}")
  public Effect<DeviceEntity.State> get(@PathVariable String deviceId) {
    log.debug("EntityId: {}\nDeviceId: {}\nState: {}", entityId, deviceId, currentState());
    if (currentState().isEmpty()) {
      return effects().error("Device: '%s' not created".formatted(deviceId));
    }
    return effects().reply(currentState());
  }

  @EventHandler
  public State on(DeviceCreatedEvent event) {
    log.debug("State: {}\nEvent: {}", currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(AlarmChangedEvent event) {
    log.debug("State: {}\nEvent: {}", currentState(), event);
    return currentState().on(event);
  }

  public record State(String deviceId, LatLng position, Instant lastPing, boolean alarmOn, Instant alarmLastTriggered) {

    static State empty() {
      return new State(null, latLng(0, 0), null, false, null);
    }

    boolean isEmpty() {
      return deviceId == null;
    }

    DeviceCreatedEvent eventFor(CreateDeviceCommand command) {
      return new DeviceCreatedEvent(command.deviceId, command.position);
    }

    List<?> eventsFor(String deviceId) {
      if (alarmOn && random.nextDouble() * 100 > 95) {
        return List.of(new AlarmChangedEvent(deviceId, position, true, Instant.now()));
      } else if (!alarmOn && random.nextDouble() * 1_000 > 995) {
        return List.of(new AlarmChangedEvent(deviceId, position, false, Instant.now()));
      }
      return List.of();
    }

    State on(DeviceCreatedEvent event) {
      if (deviceId != null) {
        return this;
      }
      return new State(event.deviceId, event.position, Instant.ofEpochSecond(0), alarmOn, Instant.now());
    }

    State on(AlarmChangedEvent event) {
      return new State(deviceId, position, Instant.now(), event.alarmOn, event.alarmLastTriggered);
    }
  }

  static String deviceIdFor(LatLng position) {
    return "device-id_%1.13f_%1.13f".formatted(position.lat(), position.lng());
  }

  public record CreateDeviceCommand(String deviceId, LatLng position) {}

  public record DeviceCreatedEvent(String deviceId, LatLng position) {}

  public record AlarmChangedEvent(String deviceId, LatLng position, boolean alarmOn, Instant alarmLastTriggered) {}
}
