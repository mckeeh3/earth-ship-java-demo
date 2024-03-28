package io.example.map;

import static io.example.map.WorldMap.earthRadiusKm;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.map.WorldMap.LatLng;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;

@Id("generatorId")
@TypeId("generator")
@RequestMapping("/generator")
public class GeneratorEntity extends EventSourcedEntity<GeneratorEntity.State, GeneratorEntity.Event> {
  private static final Logger log = LoggerFactory.getLogger(GeneratorEntity.class);
  private static final Random random = new Random();
  private final String entityId;

  public GeneratorEntity(EventSourcedEntityContext context) {
    entityId = context.entityId();
  }

  static int geoOrdersPerGeneratorBatch = 32;

  @Override
  public State emptyState() {
    return State.empty();
  }

  @PostMapping("/{generatorId}/create")
  public Effect<String> create(@RequestBody CreateGeneratorCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @PutMapping("/{generatorId}/generate")
  public Effect<String> generate(@RequestBody GenerateCommand command) {
    log.info("C-EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);
    return effects()
        .emitEvents(currentState().eventsFor(command))
        .thenReply(__ -> "OK");
  }

  @GetMapping("/{generatorId}")
  public Effect<GeneratorEntity.State> get(@PathVariable String generatorId) {
    log.info("EntityId: {}\n_State: {}\n_Get", entityId, currentState());
    if (currentState().isEmpty()) {
      return effects().error("Generator: '%s', not created".formatted(generatorId));
    }
    return effects().reply(currentState());
  }

  @EventHandler
  public State on(GeneratorCreatedEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(GeneratedEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  @EventHandler
  public State on(GeoOrdersToGenerateEvent event) {
    log.info("E-EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);
    return currentState().on(event);
  }

  public record State(
      String generatorId,
      LatLng position,
      double radiusKm,
      int ratePerSecond,
      long startTimeMs,
      int geoOrderCountLimit,
      int geoOrderCountCurrent) {

    static State empty() {
      return new State(null, null, 0, 0, epochMsNow(), 0, 0);
    }

    boolean isEmpty() {
      return generatorId == null;
    }

    List<Event> eventsFor(CreateGeneratorCommand command) {
      if (!isEmpty()) {
        return List.of(new GeneratorCreatedEvent(
            generatorId,
            position,
            radiusKm,
            ratePerSecond,
            startTimeMs,
            geoOrderCountLimit));
      }
      var generatorCreatedEvent = new GeneratorCreatedEvent(
          command.generatorId,
          command.position,
          command.radiusKm,
          command.ratePerSecond,
          epochMsNow(),
          command.geoOrderCountLimit);
      return Stream.of(List.of(generatorCreatedEvent), createGeoOrdersToGenerateEvents(command.generatorId()))
          .flatMap(List::stream)
          .map(event -> (GeneratorEntity.Event) event)
          .toList();
    }

    List<Event> eventsFor(GenerateCommand command) {
      if (geoOrderCountCurrent == geoOrderCountLimit) {
        return List.of();
      }
      var geoOrderBatches = createGeoOrdersToGenerateEvents(command.generatorId());
      var geoOrdersToBeGenerated = geoOrderBatches.stream()
          .map(e -> e.geoOrders().size())
          .reduce(0, (a, n) -> a + n);
      var generatedEvent = new GeneratedEvent(generatorId, geoOrdersToBeGenerated, geoOrderCountCurrent + geoOrdersToBeGenerated);
      return Stream.of(List.of(generatedEvent), geoOrderBatches)
          .flatMap(List::stream)
          .map(event -> (GeneratorEntity.Event) event)
          .toList();
    }

    List<GeoOrdersToGenerateEvent> createGeoOrdersToGenerateEvents(String generatorId) {
      var elapsedMs = epochMsNow() - startTimeMs;
      var geoOrdersPerBatch = geoOrdersPerGeneratorBatch;
      var geoOrdersToBeCreated = (int) Math.min(geoOrderCountLimit - geoOrderCountCurrent, (elapsedMs * ratePerSecond / 1000) - geoOrderCountCurrent);
      var geoOrderBatches = geoOrdersToBeCreated / geoOrdersPerBatch + (geoOrdersToBeCreated % geoOrdersPerBatch > 0 ? 1 : 0);
      if (geoOrderBatches == 0) {
        return List.of();
      }
      return IntStream.range(0, geoOrderBatches)
          .mapToObj(i -> (i + 1) * geoOrdersPerBatch > geoOrdersToBeCreated ? geoOrdersToBeCreated % geoOrdersPerBatch : geoOrdersPerBatch)
          .map(i -> GeoOrdersToGenerateEvent.with(generatorId, position, radiusKm, i))
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
          event.geoOrderCountLimit(),
          0);
    }

    State on(GeneratedEvent event) {
      return new State(
          event.generatorId(),
          position,
          radiusKm,
          ratePerSecond,
          startTimeMs,
          geoOrderCountLimit,
          event.geoOrderCountCurrent());
    }

    State on(GeoOrdersToGenerateEvent event) {
      return this;
    }

    static long epochMsNow() {
      return Instant.now().toEpochMilli();
    }
  }

  public interface Event {}

  public record CreateGeneratorCommand(String generatorId, LatLng position, double radiusKm, int geoOrderCountLimit, int ratePerSecond) {}

  public record GenerateCommand(String generatorId) {}

  public record GeneratorCreatedEvent(String generatorId, LatLng position, double radiusKm, int ratePerSecond, long startTimeMs, int geoOrderCountLimit) implements Event {}

  public record GeneratedEvent(String generatorId, int geoOrdersGenerated, int geoOrderCountCurrent) implements Event {}

  public record GeoOrdersToGenerateEvent(String generatorId, int geoOrdersToBeGenerated, List<GeoOrder> geoOrders) implements Event {
    static GeoOrdersToGenerateEvent with(String generatorId, LatLng position, double radiusKm, int geoOrderCount) {
      var geoOrders = generateGeoOrders(generatorId, position, radiusKm, geoOrderCount);
      return new GeoOrdersToGenerateEvent(generatorId, geoOrders.size(), geoOrders);
    }

    static List<GeoOrder> generateGeoOrders(String generatorId, LatLng position, double radiusKm, int geoOrderCount) {
      return IntStream.range(0, geoOrderCount)
          .mapToObj(i -> nextGeoOrder(generatorId, position, radiusKm))
          .toList();
    }

    static GeoOrder nextGeoOrder(String generatorId, LatLng position, double radiusKm) {
      final var angle = random.nextDouble() * 2 * Math.PI;
      final var distance = random.nextDouble() * radiusKm;
      final var lat = Math.toRadians(position.lat());
      final var lng = Math.toRadians(position.lng());
      final var lat2 = Math.asin(Math.sin(lat) * Math.cos(distance / earthRadiusKm) +
          Math.cos(lat) * Math.sin(distance / earthRadiusKm) * Math.cos(angle));
      final var lng2 = lng + Math.atan2(Math.sin(angle) * Math.sin(distance / earthRadiusKm) * Math.cos(lat),
          Math.cos(distance / earthRadiusKm) - Math.sin(lat) * Math.sin(lat2));
      var geoOrderPosition = LatLng.fromRadians(lat2, lng2);
      var geoOrderId = GeoOrderEntity.geoOrderIdFor(geoOrderPosition);
      return new GeoOrder(geoOrderId, generatorId, geoOrderPosition);
    }
  }

  public record GeoOrder(String geoOrderId, String generatorId, LatLng position) {}
}
