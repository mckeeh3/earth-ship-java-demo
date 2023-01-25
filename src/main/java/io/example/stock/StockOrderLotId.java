package io.example.stock;

public record StockOrderLotId(String stockOrderId, int lotLevel, int lotNumber) {

  static final int subLotsPerLot = 3;

  String toEntityId() {
    return "%s_%d_%d".formatted(stockOrderId, lotLevel, lotNumber);
  }

  StockOrderLotId levelUp() {
    if (lotLevel == 0) {
      return this;
    }
    var newLotLevel = lotLevel - 1;
    var newLotNumber = lotNumber / subLotsPerLot;

    return new StockOrderLotId(stockOrderId, newLotLevel, newLotNumber);
  }
}
