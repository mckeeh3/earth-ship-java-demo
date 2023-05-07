package io.example.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.any.Any;

import io.example.LogEvent;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.spring.KalixClient;
import kalix.javasdk.annotations.Subscribe;

@Subscribe.EventSourcedEntity(value = BackOrderedLotEntity.class, ignoreUnknown = true)
public class BackOrderedLotToBackOrderedLotAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(BackOrderedLotToBackOrderedLotAction.class);
  private final KalixClient kalixClient;

  public BackOrderedLotToBackOrderedLotAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  public Effect<String> on(BackOrderedLotEntity.UpdatedBackOrderedLotEvent event) {
    log.info("Event: {}", event);
    return effects().forward(callFor(event));
  }

  public Effect<String> on(BackOrderedLotEntity.ReleasedBackOrderedLotEvent event) {
    log.info("Event: {}", event);

    if (event.backOrderedLotId().lotLevel() > 0) {
      return effects().forward(callForBackOrderedLot(event));
    } else {
      return effects().forward(callForProduct(event));
    }
  }

  private DeferredCall<Any, String> callFor(BackOrderedLotEntity.UpdatedBackOrderedLotEvent event) {
    var path = "/back-ordered-lot/%s/release".formatted(event.backOrderedLotId().toEntityId());
    var command = new BackOrderedLotEntity.ReleaseBackOrderedLotCommand(event.backOrderedLotId());
    var returnType = String.class;

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callForBackOrderedLot(BackOrderedLotEntity.ReleasedBackOrderedLotEvent event) {
    var upperBackOrderedLotId = event.backOrderedLotId().levelUp();
    var path = "/back-ordered-lot/%s/update".formatted(upperBackOrderedLotId.toEntityId());
    var command = new BackOrderedLotEntity.UpdateSubBackOrderedLotCommand(event.backOrderedLotId(), event.backOrderedLot());
    var returnType = String.class;

    LogEvent.log("BackOrderedLot", event.backOrderedLotId().toEntityId(), "BackOrderedLot", upperBackOrderedLotId.toEntityId(), "");

    return kalixClient.put(path, command, returnType);
  }

  private DeferredCall<Any, String> callForProduct(BackOrderedLotEntity.ReleasedBackOrderedLotEvent event) {
    var skuId = event.backOrderedLotId().skuId();
    var path = "/product/%s/update-units-back-ordered".formatted(skuId);
    var command = new ProductEntity.UpdateProductsBackOrderedCommand(skuId, event.backOrderedLot());
    var returnType = String.class;

    LogEvent.log("BackOrderedLot", event.backOrderedLotId().toEntityId(), "Product", skuId, "");

    return kalixClient.put(path, command, returnType);
  }
}
