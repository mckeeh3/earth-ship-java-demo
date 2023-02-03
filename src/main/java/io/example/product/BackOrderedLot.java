package io.example.product;

import java.util.List;
import java.util.stream.Stream;

public record BackOrderedLot(
    BackOrderedLotId backOrderedLotId,
    int quantityBackOrdered,
    List<BackOrderedLot> subBackOrderedLots) {

  public BackOrderedLot copyWithoutSubLots() {
    return new BackOrderedLot(backOrderedLotId, quantityBackOrdered, List.of());
  }

  public BackOrderedLot levelUp() {
    return new BackOrderedLot(backOrderedLotId.levelUp(), quantityBackOrdered, subBackOrderedLots);
  }

  public BackOrderedLot addSubLot(BackOrderedLot subBackOrderedLot) {
    var filteredLots = subBackOrderedLots.stream()
        .filter(subLot -> !subLot.backOrderedLotId().equals(subBackOrderedLot.backOrderedLotId()))
        .filter(subLot -> subLot.quantityBackOrdered() > 0);
    var addLot = Stream.of(subBackOrderedLot);
    var newSubBackOrderedLots = Stream.concat(filteredLots, addLot);

    var zeroBackOrderedLot = new BackOrderedLot(backOrderedLotId, 0, newSubBackOrderedLots.toList());
    return newSubBackOrderedLots
        .reduce(zeroBackOrderedLot, (a, s) -> new BackOrderedLot(
            a.backOrderedLotId(),
            a.quantityBackOrdered() + s.quantityBackOrdered(),
            a.subBackOrderedLots()));
  }
}
