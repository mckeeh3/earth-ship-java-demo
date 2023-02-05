package io.example.product;

import java.time.Instant;

public record BackOrderedLotId(String skuId, String orderSkuItemId, int lotLevel, long lotNumber) {

  static final int subLotsPerLot = 100;
  static final int lotLevels = (int) Math.ceil(Math.log(365.0 * 24 * 60 * 60 * 1000) / Math.log(subLotsPerLot));

  String toEntityId() {
    return "%s%s_%d_%d".formatted(skuId, orderSkuItemId, lotLevel, lotNumber);
  }

  BackOrderedLotId levelUp() {
    if (lotLevel == 0) {
      return this;
    }
    var newLotLevel = lotLevel - 1;
    var newLotNumber = lotNumber / subLotsPerLot;

    return new BackOrderedLotId(skuId, "", newLotLevel, newLotNumber);
  }

  public static BackOrderedLotId of(String skuId, String orderItemId, Instant orderedAt) {
    var lotLevel = lotLevels;
    var lotNumber = orderedAt.toEpochMilli();
    return new BackOrderedLotId(skuId, orderItemId, lotLevel, lotNumber);
  }
}
