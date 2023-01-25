package io.example.stock;

public record StockSkuItemId(StockOrderLotId stockOrderLotId, int stockSkuItemNumber) {
  String toEntityId() {
    return "%s_%d".formatted(stockOrderLotId.toEntityId(), stockSkuItemNumber);
  }

  static int lotLevelsFor(int stockOrderItemsTotal) {
    return (int) Math.ceil(Math.log(stockOrderItemsTotal) / Math.log(StockOrderLotId.subLotsPerLot));
  }

  static int lotNumberFor(int thisStockOrderItemNumber) {
    return Math.round(thisStockOrderItemNumber / StockOrderLotId.subLotsPerLot);
  }

  static StockSkuItemId of(String stockOrderId, int stockOrderItemsTotal, int thisStockOrderItemNumber) {
    var lotLevel = lotLevelsFor(stockOrderItemsTotal) - 1;
    var lotNumber = lotNumberFor(thisStockOrderItemNumber);
    var stockOrderLotId = new StockOrderLotId(stockOrderId, lotLevel, lotNumber);
    return new StockSkuItemId(stockOrderLotId, thisStockOrderItemNumber);
  }

  StockOrderLotId levelUp() {
    return stockOrderLotId.levelUp();
  }
}
