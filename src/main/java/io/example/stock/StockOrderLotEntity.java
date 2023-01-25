package io.example.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;

import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;

@EntityKey("stockOrderLotId")
@EntityType("stockOrderLot")
@RequestMapping("/stockOrderLot/{stockOrderLotId}")
public class StockOrderLotEntity extends EventSourcedEntity<StockOrderLotEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(StockOrderLotEntity.class);
  private final String entityId;

  public StockOrderLotEntity(String entityId) {
    this.entityId = entityId;
  }

  public record State(
      StockOrderLot stockOrderLot,
      boolean hasChanged) {

    static State emptyState() {
      return new State(null, false);
    }

    boolean isEmpty() {
      return stockOrderLot == null;
    }
  }

  public record StockOrderLot(
      StockOrderLotId stockOrderLotId,
      int quantityTotal,
      int quantityOrdered,
      List<StockOrderLot> lotItems) {}

  public record UpdateSubStockOrderLotCommand(StockOrderLotId subStockOrderLotId, StockOrderLot stockOrderLot) {}

  public record UpdatedSubStockOrderLotEvent(StockOrderLotId subStockOrderLotId, StockOrderLot stockOrderLot) {}

  public record UpdatedStockOrderEvent(String stockOrderId) {}

  public record ReleaseStockOrderLotCommand(StockOrderLotId stockOrderLotId) {}

  public record ReleasedStockOrderLotEvent(StockOrderLotId stockOrderLotId, StockOrderLot stockOrderLot) {}
}
