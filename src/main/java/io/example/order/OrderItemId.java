package io.example.order;

public record OrderItemId(String orderId, String skuId) {
  public static OrderItemId of(String orderId, String skuId) {
    return new OrderItemId(orderId, skuId);
  }

  public String toEntityId() {
    return "%s_%s".formatted(orderId, skuId);
  }
}
