package io.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = BackOrderedLotEntity.class, ignoreUnknown = true)
public class BackOrderedLotToBackOrderedLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(BackOrderedLotToBackOrderedLotAction.class);
  private final ComponentClient componentClient;

  public BackOrderedLotToBackOrderedLotAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(BackOrderedLotEntity.UpdatedBackOrderedLotEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  public Effect<String> on(BackOrderedLotEntity.ReleasedBackOrderedLotEvent event) {
    log.info("Event: {}", event);

    return callFor(event);
  }

  private Effect<String> callFor(BackOrderedLotEntity.UpdatedBackOrderedLotEvent event) {
    var command = new BackOrderedLotEntity.ReleaseBackOrderedLotCommand(event.backOrderedLotId());
    return effects().forward(
        componentClient.forEventSourcedEntity(event.backOrderedLotId().toEntityId())
            .call(BackOrderedLotEntity::release)
            .params(command));
  }

  private Effect<String> callFor(BackOrderedLotEntity.ReleasedBackOrderedLotEvent event) {
    if (event.backOrderedLotId().lotLevel() > 0) {
      return callForBackOrderedLot(event);
    } else {
      return callForProduct(event);
    }
  }

  private Effect<String> callForBackOrderedLot(BackOrderedLotEntity.ReleasedBackOrderedLotEvent event) {
    var upperBackOrderedLotId = event.backOrderedLotId().levelUp();
    var command = new BackOrderedLotEntity.UpdateSubBackOrderedLotCommand(event.backOrderedLotId(), event.backOrderedLot());

    var message = "color %s".formatted(event.backOrderedLot().quantityBackOrdered() > 0 ? "red" : "green");
    LogEvent.log("BackOrderedLot", event.backOrderedLotId().toEntityId(), "BackOrderedLot", upperBackOrderedLotId.toEntityId(), message);

    return effects().forward(
        componentClient.forEventSourcedEntity(upperBackOrderedLotId.toEntityId())
            .call(BackOrderedLotEntity::update)
            .params(command));
  }

  private Effect<String> callForProduct(BackOrderedLotEntity.ReleasedBackOrderedLotEvent event) {
    var skuId = event.backOrderedLotId().skuId();
    var command = new ProductEntity.UpdateProductsBackOrderedCommand(skuId, event.backOrderedLot());

    var message = "color %s".formatted(event.backOrderedLot().quantityBackOrdered() > 0 ? "red" : "green");
    LogEvent.log("BackOrderedLot", event.backOrderedLotId().toEntityId(), "Product", skuId, message);

    return effects().forward(
        componentClient.forEventSourcedEntity(skuId)
            .call(ProductEntity::updateUnitsBackOrdered)
            .params(command));
  }
}
