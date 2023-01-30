package io.example.stock;

import java.util.List;

public record StockOrderLot(
    StockOrderLotId stockOrderLotId,
    int quantityTotal,
    int quantityOrdered,
    List<StockOrderLot> subStockOrderLots) {

  public StockOrderLot copyWithoutSubLots() {
    return new StockOrderLot(stockOrderLotId, quantityTotal, quantityOrdered, List.of());
  }
}
