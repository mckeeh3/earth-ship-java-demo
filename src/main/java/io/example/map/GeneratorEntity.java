package io.example.map;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

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

import static io.example.map.WorldMap.*;

@EntityKey("generatorId")
@EntityType("generator")
@RequestMapping("/generator")
public class GeneratorEntity extends EventSourcedEntity<GeneratorEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(GeneratorEntity.class);
  private static final Random random = new Random();
  private final String entityId;

  public GeneratorEntity(EventSourcedEntityContext context) {
    entityId = context.entityId();
  }

  static int devicesPerGeneratorBatch = 32;

  @Override
  public State emptyState() {
    return State.empty();
  }

  @PostMapping("/{generatorId}/create")
  public Effect<String> create(@RequestBody CreateGeneratorCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/{generatorId}/generate")
  public Effect<String> generate(@RequestBody GenerateCommand command) {
    log.info("EntityId: {}\nState: {}\nCommand: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping("/{generatorId}")
  public Effect<GeneratorEntity.State> get(@PathVariable String generatorId) {
    log.info("EntityId: {}\nGeneratorId: {}\nState: {}", entityId, generatorId, currentState());
    if (currentState().isEmpty()) {
      return effects().error("Generator: '%s', not created".formatted(generatorId));
    }
    return effects().reply(currentState());
  }

  @EventHandler
  public State on(GeneratorCreatedEvent event) {
    log.info("State: {}\nEvent: {}", currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(GeneratedEvent event) {
    log.info("State: {}\nEvent: {}", currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(DevicesToGenerateEvent event) {
    log.info("State: {}\nEvent: {}", currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String generatorId,
      LatLng position,
      double radiusKm,
      int ratePerSecond,
      long startTimeMs,
      int deviceCountLimit,
      int deviceCountCurrent) {

    static State empty() {
      return new State(null, null, 0, 0, epochMsNow(), 0, 0);
    }

    boolean isEmpty() {
      return generatorId == null;
    }

    List<?> eventsFor(CreateGeneratorCommand command) {
      if (!isEmpty()) {
        return List.of(new GeneratorCreatedEvent(
            generatorId,
            position,
            radiusKm,
            ratePerSecond,
            startTimeMs,
            deviceCountLimit));
      }
      var generatorCreatedEvent = new GeneratorCreatedEvent(
          command.generatorId,
          command.position,
          command.radiusKm,
          command.ratePerSecond,
          epochMsNow(),
          command.deviceCountLimit);
      var events = new ArrayList<Object>();
      events.add(generatorCreatedEvent);
      events.addAll(createDevicesToGenerateEvents(command.generatorId()));
      return events;
    }

    List<?> eventsFor(GenerateCommand command) {
      if (deviceCountCurrent == deviceCountLimit) {
        return List.of();
      }
      var deviceBatches = createDevicesToGenerateEvents(command.generatorId());
      var devicesToBeGenerated = deviceBatches.stream()
          .map(e -> e.devices().size())
          .reduce(0, (a, n) -> a + n);
      var events = new ArrayList<Object>();
      events.add(new GeneratedEvent(generatorId, devicesToBeGenerated, deviceCountCurrent + devicesToBeGenerated));
      events.addAll(deviceBatches);
      return events;
    }

    List<DevicesToGenerateEvent> createDevicesToGenerateEvents(String generatorId) {
      var elapsedMs = epochMsNow() - startTimeMs;
      var devicesPerBatch = devicesPerGeneratorBatch;
      var devicesToBeCreated = (int) Math.min(deviceCountLimit - deviceCountCurrent, (elapsedMs * ratePerSecond / 1000) - deviceCountCurrent);
      var deviceBatches = devicesToBeCreated / devicesPerBatch + (devicesToBeCreated % devicesPerBatch > 0 ? 1 : 0);
      if (deviceBatches == 0) {
        return List.of();
      }
      return IntStream.range(0, deviceBatches)
          .mapToObj(i -> (i + 1) * devicesPerBatch > devicesToBeCreated ? devicesToBeCreated % devicesPerBatch : devicesPerBatch)
          .map(i -> DevicesToGenerateEvent.with(generatorId, position, radiusKm, i))
          .toList();
    }

    State on(GeneratorCreatedEvent event) {
      if (generatorId != null) {
        return this;
      }
      return new State(
          event.generatorId(),
          event.position(),
          event.radiusKm(),
          event.ratePerSecond(),
          event.startTimeMs(),
          event.deviceCountLimit(),
          0);
    }

    State on(GeneratedEvent event) {
      return new State(
          event.generatorId(),
          position,
          radiusKm,
          ratePerSecond,
          startTimeMs,
          deviceCountLimit,
          event.deviceCountCurrent());
    }

    State on(DevicesToGenerateEvent event) {
      return this;
    }

    static long epochMsNow() {
      return Instant.now().toEpochMilli();
    }
  }

  public record CreateGeneratorCommand(String generatorId, LatLng position, double radiusKm, int deviceCountLimit, int ratePerSecond) {}

  public record GenerateCommand(String generatorId) {}

  public record GeneratorCreatedEvent(String generatorId, LatLng position, double radiusKm, int ratePerSecond, long startTimeMs, int deviceCountLimit) {}

  public record GeneratedEvent(String generatorId, int devicesGenerated, int deviceCountCurrent) {}

  public record DevicesToGenerateEvent(String generatorId, int devicesToBeGenerated, List<Device> devices) {
    static DevicesToGenerateEvent with(String generatorId, LatLng position, double radiusKm, int deviceCount) {
      var devices = generateDevices(generatorId, position, radiusKm, deviceCount);
      return new DevicesToGenerateEvent(generatorId, devices.size(), devices);
    }

    static List<Device> generateDevices(String generatorId, LatLng position, double radiusKm, int deviceCount) {
      return IntStream.range(0, deviceCount)
          .mapToObj(i -> nextDevice(generatorId, position, radiusKm))
          .toList();
    }

    static Device nextDevice(String generatorId, LatLng position, double radiusKm) {
      final var angle = random.nextDouble() * 2 * Math.PI;
      final var distance = random.nextDouble() * radiusKm;
      final var lat = Math.toRadians(position.lat());
      final var lng = Math.toRadians(position.lng());
      final var lat2 = Math.asin(Math.sin(lat) * Math.cos(distance / earthRadiusKm) +
          Math.cos(lat) * Math.sin(distance / earthRadiusKm) * Math.cos(angle));
      final var lng2 = lng + Math.atan2(Math.sin(angle) * Math.sin(distance / earthRadiusKm) * Math.cos(lat),
          Math.cos(distance / earthRadiusKm) - Math.sin(lat) * Math.sin(lat2));
      var devicePosition = LatLng.fromRadians(lat2, lng2);
      var deviceId = DeviceEntity.deviceIdFor(devicePosition);
      return new Device(deviceId, generatorId, devicePosition);
    }
  }

  public record Device(String deviceId, String generatorId, LatLng position) {}
}
