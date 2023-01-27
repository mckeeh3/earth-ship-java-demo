package io.example.shipping;

import java.util.UUID;

public record OrderSkuItemId(String orderId, UUID uuid) {
  static OrderSkuItemId of(String orderId) {
    return new OrderSkuItemId(orderId, UUID.randomUUID());
  }
}
