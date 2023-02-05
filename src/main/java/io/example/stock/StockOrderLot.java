package io.example.stock;

import java.util.List;
import java.util.stream.Stream;

public record StockOrderLot(
    StockOrderLotId stockOrderLotId,
    int quantityTotal,
    int quantityOrdered,
    List<StockOrderLot> subStockOrderLots) {

  public StockOrderLot copyWithoutSubLots() {
    return new StockOrderLot(stockOrderLotId, quantityTotal, quantityOrdered, List.of());
  }

  public StockOrderLot addSubLot(StockOrderLot subStockOrderLot) {
    var filteredLots = subStockOrderLots.stream()
        .filter(subLot -> !subLot.stockOrderLotId().equals(subStockOrderLot.stockOrderLotId()));
    var addLot = Stream.of(subStockOrderLot);
    var newSubStockOrderLots = Stream.concat(filteredLots, addLot).toList();

    var zeroStockOrderLot = new StockOrderLot(stockOrderLotId, 0, 0, newSubStockOrderLots);
    return newSubStockOrderLots.stream()
        .reduce(zeroStockOrderLot, (a, s) -> new StockOrderLot(
            a.stockOrderLotId(),
            a.quantityTotal() + s.quantityTotal(),
            a.quantityOrdered() + s.quantityOrdered(),
            a.subStockOrderLots()));
  }
}
