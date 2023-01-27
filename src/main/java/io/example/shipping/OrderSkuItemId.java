package io.example.shipping;

import java.util.UUID;

public record OrderSkuItemId(String orderId, UUID uuid) {
  public static OrderSkuItemId of(String orderId) {
    return new OrderSkuItemId(orderId, UUID.randomUUID());
  }

  public String toEntityId() {
    return "%s_%s".formatted(orderId, uuid);
  }
}
