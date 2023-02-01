package io.example.stock;

public record StockSkuItemId(StockOrderLotId stockOrderLotId, int stockSkuItemNumber) {
  String toEntityId() {
    return "%s_%d".formatted(stockOrderLotId.toEntityId(), stockSkuItemNumber);
  }

  private static int lotLevelsFor(int stockOrderItemsTotal) {
    return (int) Math.ceil(Math.log(stockOrderItemsTotal) / Math.log(StockOrderLotId.subLotsPerLot));
  }

  static StockSkuItemId of(String stockOrderId, int stockOrderItemsTotal, int stockOrderItemNumber) {
    var lotLevel = lotLevelsFor(stockOrderItemsTotal);
    var lotNumber = stockOrderItemNumber;
    var stockOrderLotId = new StockOrderLotId(stockOrderId, lotLevel, lotNumber);
    return new StockSkuItemId(stockOrderLotId, stockOrderItemNumber);
  }

  StockOrderLotId levelUp() {
    return stockOrderLotId.levelUp();
  }
}
